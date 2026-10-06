package DGU_AI_LAB.admin_be.domain.scheduler;

import DGU_AI_LAB.admin_be.domain.home.service.HomeCleanupNotifier;
import DGU_AI_LAB.admin_be.domain.home.service.HomeCleanupService;
import DGU_AI_LAB.admin_be.domain.home.service.HomeRetentionPolicy;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.HomeDeleteRegisterRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.job.JobClient;
import DGU_AI_LAB.admin_be.domain.requests.job.JobRegistrationUnconfirmedException;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 매일 한 번, 마지막 컨테이너가 끝나고 보존 기간이 지난 계정의 홈 삭제 작업을 등록한다. 결과는
 * {@link HomeCleanupJobPoller}가 반영한다. 기간은 {@link HomeRetentionPolicy} 참고.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HomeCleanupScheduler {

    /** 등록이 거절·실패해 작업이 만들어지지 않았을 때 남기는 실패 코드. */
    static final String REGISTRATION_FAILED = "REGISTRATION_FAILED";

    private final UserRepository userRepository;
    private final HomeCleanupService homeCleanupService;
    private final HomeRetentionPolicy retentionPolicy;
    private final JobClient jobClient;
    private final HomeCleanupNotifier notifier;

    @Scheduled(cron = "${home-retention.cleanup-cron:0 30 9 * * ?}", zone = "Asia/Seoul")
    public void runHomeCleanup() {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        for (User user : userRepository.findHomeCleanupCandidates(
                retentionPolicy.deletableEndedBefore(now), Status.openStatuses())) {
            try {
                // 사용자별 독립 트랜잭션 — 한 사용자의 실패가 나머지를 막지 않게 한다.
                homeCleanupService.begin(user.getUserId(), now).ifPresent(this::register);
            } catch (Exception e) {
                log.error("[homeCleanup] 홈 삭제 등록 중 오류: userId={}", user.getUserId(), e);
            }
        }
    }

    private void register(HomeCleanupService.Target target) {
        try {
            Long jobId = jobClient.registerHomeDelete(new HomeDeleteRegisterRequestDTO(
                    target.cleanupId(), target.ubuntuUsername(), target.ubuntuUid()));
            homeCleanupService.recordJob(target.cleanupId(), jobId);
            log.info("[homeCleanup] 홈 삭제 작업 등록: cleanupId={}, username={}, jobId={}",
                    target.cleanupId(), target.ubuntuUsername(), jobId);
        } catch (JobRegistrationUnconfirmedException e) {
            // 작업이 등록돼 돌고 있을 수 있다. 실패로 단정하지 않고 폴러가 결과로 판단하게 둔다.
            log.warn("[homeCleanup] 등록 응답을 받지 못함 — 폴러가 결과를 확인한다: cleanupId={}", target.cleanupId(), e);
        } catch (Exception e) {
            log.error("[homeCleanup] 홈 삭제 작업 등록 실패: cleanupId={}", target.cleanupId(), e);
            homeCleanupService.fail(target.cleanupId(), REGISTRATION_FAILED)
                    .ifPresent(failed -> notifier.failed(failed, REGISTRATION_FAILED));
        }
    }
}
