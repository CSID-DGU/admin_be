package DGU_AI_LAB.admin_be.domain.home.service;

import DGU_AI_LAB.admin_be.domain.home.entity.HomeCleanup;
import DGU_AI_LAB.admin_be.domain.home.entity.HomeCleanupStatus;
import DGU_AI_LAB.admin_be.domain.home.repository.HomeCleanupRepository;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 홈 삭제 시도의 상태 전이. 작업 등록·결과 조회 같은 외부 호출은 여기서 하지 않는다 — 호출자가 트랜잭션 밖에서 한다.
 */
@Service
@RequiredArgsConstructor
public class HomeCleanupService {

    /** 같은 종료 시각에 대해 다시 등록하지 않는 상태. 실패한 시도는 다음 날 다시 한다. */
    private static final List<HomeCleanupStatus> SETTLED_OR_RUNNING =
            List.of(HomeCleanupStatus.PROCESSING, HomeCleanupStatus.DELETED);

    private final UserRepository userRepository;
    private final HomeCleanupRepository cleanupRepository;
    private final HomeRetentionPolicy retentionPolicy;

    /** 등록할 작업의 내용. 엔티티를 트랜잭션 밖으로 내보내지 않으려고 값만 담는다. */
    public record Target(Long cleanupId, String ubuntuUsername, Long ubuntuUid, LocalDateTime lastContainerEndedAt) {
        static Target of(HomeCleanup cleanup) {
            return new Target(cleanup.getHomeCleanupId(), cleanup.getUbuntuUsername(), cleanup.getUbuntuUid(),
                    cleanup.getLastContainerEndedAt());
        }
    }

    /**
     * 지워야 할 홈이면 삭제 시도를 만든다. 후보 조회 뒤 새로 신청했거나 이미 처리됐을 수 있어 여기서 다시 본다.
     *
     * @return 작업을 등록해야 하면 그 내용
     */
    @Transactional
    public Optional<Target> begin(Long userId, LocalDateTime now) {
        User user = userRepository.findById(userId).orElseThrow();
        if (user.getUbuntuUsername() == null || user.getUbuntuUid() == null || user.hasOpenRequest()) {
            return Optional.empty();
        }
        Optional<LocalDateTime> endedAt = user.lastContainerEndedAt()
                .filter(ended -> ended.isBefore(retentionPolicy.deletableEndedBefore(now)));
        if (endedAt.isEmpty() || cleanupRepository.existsByUser_UserIdAndStatusInAndLastContainerEndedAtGreaterThanEqual(
                userId, SETTLED_OR_RUNNING, endedAt.get())) {
            return Optional.empty();
        }
        return Optional.of(Target.of(cleanupRepository.save(HomeCleanup.start(user, endedAt.get()))));
    }

    @Transactional
    public void recordJob(Long cleanupId, Long jobId) {
        cleanupRepository.findByIdForUpdate(cleanupId)
                .filter(HomeCleanup::isProcessing)
                .ifPresent(cleanup -> cleanup.registered(jobId));
    }

    /** @return 이번 호출로 끝난 시도. 이미 끝나 있었으면 비어 있다 — 알림이 두 번 나가지 않게 한다 */
    @Transactional
    public Optional<Target> complete(Long cleanupId) {
        return settle(cleanupId, HomeCleanup::complete);
    }

    @Transactional
    public Optional<Target> fail(Long cleanupId, String failureCode) {
        return settle(cleanupId, cleanup -> cleanup.fail(failureCode));
    }

    private Optional<Target> settle(Long cleanupId, java.util.function.Consumer<HomeCleanup> transition) {
        return cleanupRepository.findByIdForUpdate(cleanupId)
                .filter(HomeCleanup::isProcessing)
                .map(cleanup -> {
                    transition.accept(cleanup);
                    return Target.of(cleanup);
                });
    }
}
