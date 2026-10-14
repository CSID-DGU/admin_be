package DGU_AI_LAB.admin_be.domain.scheduler;

import DGU_AI_LAB.admin_be.domain.home.entity.HomeCleanup;
import DGU_AI_LAB.admin_be.domain.home.entity.HomeCleanupStatus;
import DGU_AI_LAB.admin_be.domain.home.repository.HomeCleanupRepository;
import DGU_AI_LAB.admin_be.domain.home.service.HomeCleanupNotifier;
import DGU_AI_LAB.admin_be.domain.home.service.HomeCleanupService;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.JobResultResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.job.JobClient;
import DGU_AI_LAB.admin_be.domain.requests.job.JobResults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 진행 중(PROCESSING)인 홈 삭제 시도마다 작업 결과를 조회해 반영한다.
 *
 * <ul>
 *   <li>SUCCESS → DELETED(홈이 이미 없었던 경우 포함)</li>
 *   <li>그 밖에 끝난 결과(FAIL·UNKNOWN·기록 없음) → FAILED로 닫고 관리자에게 알림. 다음 날 새 시도가 생긴다</li>
 *   <li>START·RETRY → 다음 바퀴에 다시 본다</li>
 *   <li>이번에 등록한 작업이 아닌 결과는 반영하지 않는다</li>
 *   <li>작업 번호가 없는 시도(등록 결과를 확인하지 못함)는 등록 대기 시간이 지난 뒤 최신 결과로 판단한다</li>
 * </ul>
 */
@Slf4j
@Component
public class HomeCleanupJobPoller extends OperationJobPoller<HomeCleanup> {

    /** 결과에 오류 코드가 없을 때(결과 불명·기록 없음) 남기는 실패 코드. */
    static final String RESULT_UNKNOWN = "RESULT_UNKNOWN";

    private final HomeCleanupRepository cleanupRepository;
    private final HomeCleanupService homeCleanupService;
    private final HomeCleanupNotifier notifier;

    public HomeCleanupJobPoller(HomeCleanupRepository cleanupRepository, JobClient jobClient,
                                HomeCleanupService homeCleanupService, HomeCleanupNotifier notifier) {
        super(jobClient, JobResults.KIND_HOME);
        this.cleanupRepository = cleanupRepository;
        this.homeCleanupService = homeCleanupService;
        this.notifier = notifier;
    }

    @Scheduled(fixedDelayString = "${operations.home.poll-ms:30000}")
    public void pollHomeCleanups() {
        pollEach(cleanupRepository.findAllByStatus(HomeCleanupStatus.PROCESSING));
    }

    @Override
    protected Long recordId(HomeCleanup cleanup) {
        return cleanup.getHomeCleanupId();
    }

    @Override
    protected Long jobId(HomeCleanup cleanup) {
        return cleanup.getJobId();
    }

    @Override
    protected LocalDateTime registrationWaitSince(HomeCleanup cleanup) {
        return cleanup.getUpdatedAt();
    }

    @Override
    protected void onSuccess(HomeCleanup cleanup, JobResultResponseDTO result) {
        homeCleanupService.complete(cleanup.getHomeCleanupId()).ifPresent(notifier::deleted);
    }

    @Override
    protected void onEndedWithoutSuccess(HomeCleanup cleanup, JobResultResponseDTO result) {
        Long cleanupId = cleanup.getHomeCleanupId();
        String failureCode = failureCode(result, RESULT_UNKNOWN);
        homeCleanupService.fail(cleanupId, failureCode).ifPresent(failed -> {
            log.error("[homeCleanup] 홈 삭제 실패: cleanupId={}, phase={}, error={}",
                    cleanupId, result.phase(), result.errorCode());
            notifier.failed(failed, failureCode);
        });
    }
}
