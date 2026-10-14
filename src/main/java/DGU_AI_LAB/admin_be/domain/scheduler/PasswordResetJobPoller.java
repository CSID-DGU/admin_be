package DGU_AI_LAB.admin_be.domain.scheduler;

import DGU_AI_LAB.admin_be.domain.requests.dto.response.JobResultResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.job.JobClient;
import DGU_AI_LAB.admin_be.domain.requests.job.JobResults;
import DGU_AI_LAB.admin_be.domain.users.entity.PasswordResetRequest;
import DGU_AI_LAB.admin_be.domain.users.repository.PasswordResetRequestRepository;
import DGU_AI_LAB.admin_be.domain.users.service.PasswordResetNotifier;
import DGU_AI_LAB.admin_be.domain.users.service.PasswordResetService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 컨테이너에 반영 중(PROCESSING)인 비밀번호 재설정 신청마다 작업 결과를 조회해 반영한다.
 *
 * <ul>
 *   <li>SUCCESS → 두 해시를 바꾸고 FULFILLED</li>
 *   <li>그 밖에 끝난 결과(FAIL·UNKNOWN·기록 없음) → 승인 대기로 되돌리고 관리자에게 알림</li>
 *   <li>START·RETRY → 다음 바퀴에 다시 본다</li>
 *   <li>이번에 등록한 작업이 아닌 결과는 반영하지 않는다</li>
 *   <li>작업 번호가 없는 신청(등록 결과를 확인하지 못함)은 등록 대기 시간이 지난 뒤 최신 결과로 판단한다</li>
 * </ul>
 *
 * <p>컨테이너 작업과 달리 결과 불명·일부만 바뀐 실패도 PROCESSING에 남겨 두지 않는다. 비밀번호 교체는 같은 해시를
 * 다시 쓰는 일이라, 다시 승인하면 어느 상태에서든 맞춰지기 때문이다.
 */
@Slf4j
@Component
public class PasswordResetJobPoller extends OperationJobPoller<PasswordResetRequest> {

    private final PasswordResetRequestRepository resetRepository;
    private final PasswordResetService passwordResetService;
    private final PasswordResetNotifier notifier;

    public PasswordResetJobPoller(PasswordResetRequestRepository resetRepository, JobClient jobClient,
                                  PasswordResetService passwordResetService, PasswordResetNotifier notifier) {
        super(jobClient, JobResults.KIND_PASSWORD);
        this.resetRepository = resetRepository;
        this.passwordResetService = passwordResetService;
        this.notifier = notifier;
    }

    @Scheduled(fixedDelayString = "${operations.password.poll-ms:3000}")
    public void pollPasswordResets() {
        pollEach(resetRepository.findAllByStatus(Status.PROCESSING));
    }

    /** 작업은 재설정 신청 번호로 등록한다. 신청의 상태는 변경 요청 번호로 바꾼다. */
    @Override
    protected Long recordId(PasswordResetRequest reset) {
        return reset.getPasswordResetRequestId();
    }

    @Override
    protected Long jobId(PasswordResetRequest reset) {
        return reset.getJobId();
    }

    /** 승인 시각은 변경 요청에 남는다 — 작업 번호 없이 승인되면 이 행은 바뀌지 않아 고친 시각이 그대로다. */
    @Override
    protected LocalDateTime registrationWaitSince(PasswordResetRequest reset) {
        return reset.getChangeRequest().getUpdatedAt();
    }

    @Override
    protected void onSuccess(PasswordResetRequest reset, JobResultResponseDTO result) {
        passwordResetService.complete(reset.getChangeRequest().getChangeRequestId());
    }

    @Override
    protected void onEndedWithoutSuccess(PasswordResetRequest reset, JobResultResponseDTO result) {
        Long changeRequestId = reset.getChangeRequest().getChangeRequestId();
        // 작업 실패는 되돌린 뒤의 실패다. 되돌리지 못했거나(DEGRADED) 결과를 모르면 일부만 바뀌었을 수 있다.
        boolean mayBePartial = !JobResults.PHASE_FAIL.equals(result.phase()) || JobResults.isDegraded(result);
        passwordResetService.returnToPending(changeRequestId).ifPresent(request -> {
            log.error("[passwordReset] 컨테이너 반영 실패 — 승인 대기로 되돌림: changeRequestId={}, phase={}, error={}",
                    changeRequestId, result.phase(), result.errorCode());
            notifier.jobFailed(request, result.phase(), result.errorCode(), mayBePartial);
        });
    }
}
