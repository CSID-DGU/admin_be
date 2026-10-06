package DGU_AI_LAB.admin_be.domain.scheduler;

import DGU_AI_LAB.admin_be.domain.home.entity.HomeCleanup;
import DGU_AI_LAB.admin_be.domain.home.entity.HomeCleanupStatus;
import DGU_AI_LAB.admin_be.domain.home.repository.HomeCleanupRepository;
import DGU_AI_LAB.admin_be.domain.home.service.HomeCleanupNotifier;
import DGU_AI_LAB.admin_be.domain.home.service.HomeCleanupService;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.JobResultResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.job.JobClient;
import DGU_AI_LAB.admin_be.domain.requests.job.JobResults;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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
@RequiredArgsConstructor
public class HomeCleanupJobPoller {

    /** 결과에 오류 코드가 없을 때(결과 불명·기록 없음) 남기는 실패 코드. */
    static final String RESULT_UNKNOWN = "RESULT_UNKNOWN";

    private final HomeCleanupRepository cleanupRepository;
    private final JobClient jobClient;
    private final HomeCleanupService homeCleanupService;
    private final HomeCleanupNotifier notifier;

    @Scheduled(fixedDelayString = "${operations.home.poll-ms:30000}")
    public void pollHomeCleanups() {
        for (HomeCleanup cleanup : cleanupRepository.findAllByStatus(HomeCleanupStatus.PROCESSING)) {
            Long cleanupId = cleanup.getHomeCleanupId();
            try {
                // 작업이 아직 기록되지 않았을 수 있어, 지금 조회하면 기록 없음을 이번 것으로 읽는다.
                if (JobResults.awaitingRegistration(cleanup.getJobId(), cleanup.getUpdatedAt())) {
                    continue;
                }
                advance(cleanupId, cleanup.getJobId());
            } catch (Exception e) {
                // 조회 실패는 대개 일시적이라 다음 바퀴에 다시 본다.
                log.warn("[homeCleanup] 작업 결과 반영 실패 - 다음 바퀴에 다시 본다: cleanupId={}", cleanupId, e);
            }
        }
    }

    private void advance(Long cleanupId, Long jobId) {
        JobResultResponseDTO result = jobClient.getResult(JobResults.KIND_HOME, cleanupId);
        if (JobResults.isFromOtherJob(jobId, result) || JobResults.isRunning(result.phase())) {
            return;
        }
        if (JobResults.PHASE_SUCCESS.equals(result.phase())) {
            homeCleanupService.complete(cleanupId).ifPresent(notifier::deleted);
            return;
        }
        String failureCode = result.errorCode() != null ? result.errorCode() : RESULT_UNKNOWN;
        homeCleanupService.fail(cleanupId, failureCode).ifPresent(failed -> {
            log.error("[homeCleanup] 홈 삭제 실패: cleanupId={}, phase={}, error={}",
                    cleanupId, result.phase(), result.errorCode());
            notifier.failed(failed, failureCode);
        });
    }
}
