package DGU_AI_LAB.admin_be.domain.scheduler;

import DGU_AI_LAB.admin_be.domain.requests.dto.response.JobResultResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.job.JobClient;
import DGU_AI_LAB.admin_be.domain.requests.job.JobResults;
import DGU_AI_LAB.admin_be.domain.users.entity.PasswordResetRequest;
import DGU_AI_LAB.admin_be.domain.users.entity.PasswordResetStatus;
import DGU_AI_LAB.admin_be.domain.users.repository.PasswordResetRequestRepository;
import DGU_AI_LAB.admin_be.domain.users.service.PasswordResetNotifier;
import DGU_AI_LAB.admin_be.domain.users.service.PasswordResetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 컨테이너에 반영 중(PROCESSING)인 비밀번호 재설정 신청마다 작업 결과를 조회해 반영한다.
 *
 * <ul>
 *   <li>SUCCESS → 두 해시를 바꾸고 APPLIED</li>
 *   <li>그 밖에 끝난 결과(FAIL·UNKNOWN·기록 없음) → 승인 대기로 되돌리고 관리자에게 알림</li>
 *   <li>START·RETRY → 다음 바퀴에 다시 본다</li>
 *   <li>이번에 등록한 작업이 아닌 결과는 반영하지 않는다</li>
 * </ul>
 *
 * <p>컨테이너 작업과 달리 결과 불명·일부만 바뀐 실패도 PROCESSING에 남겨 두지 않는다. 비밀번호 교체는 같은 해시를
 * 다시 쓰는 일이라, 다시 승인하면 어느 상태에서든 맞춰지기 때문이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PasswordResetJobPoller {

    private final PasswordResetRequestRepository resetRepository;
    private final JobClient jobClient;
    private final PasswordResetService passwordResetService;
    private final PasswordResetNotifier notifier;

    @Scheduled(fixedDelayString = "${operations.password.poll-ms:3000}")
    public void pollPasswordResets() {
        for (PasswordResetRequest reset : resetRepository.findAllByStatus(PasswordResetStatus.PROCESSING)) {
            Long resetId = reset.getPasswordResetRequestId();
            try {
                advance(resetId, reset.getJobId());
            } catch (Exception e) {
                // 한 신청의 실패가 나머지 신청 처리를 막지 않게 한다. 조회 실패는 대개 일시적이라 다음 바퀴에 다시 본다.
                log.warn("[passwordReset] 작업 결과 반영 실패 - 다음 바퀴에 다시 본다: resetId={}", resetId, e);
            }
        }
    }

    private void advance(Long resetId, Long jobId) {
        JobResultResponseDTO result = jobClient.getResult(JobResults.KIND_PASSWORD, resetId);
        if (JobResults.isFromOtherJob(jobId, result) || JobResults.isRunning(result.phase())) {
            return;
        }
        if (JobResults.PHASE_SUCCESS.equals(result.phase())) {
            passwordResetService.complete(resetId);
            return;
        }
        // 작업 실패는 되돌린 뒤의 실패다. 되돌리지 못했거나(DEGRADED) 결과를 모르면 일부만 바뀌었을 수 있다.
        boolean mayBePartial = !JobResults.PHASE_FAIL.equals(result.phase()) || JobResults.isDegraded(result);
        passwordResetService.returnToPending(resetId).ifPresent(request -> {
            log.error("[passwordReset] 컨테이너 반영 실패 — 승인 대기로 되돌림: resetId={}, phase={}, error={}",
                    resetId, result.phase(), result.errorCode());
            notifier.jobFailed(request, result.phase(), result.errorCode(), mayBePartial);
        });
    }
}
