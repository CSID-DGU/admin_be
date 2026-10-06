package DGU_AI_LAB.admin_be.domain.scheduler;

import DGU_AI_LAB.admin_be.domain.alarm.service.AlarmService;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.JobResultResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.job.JobClient;
import DGU_AI_LAB.admin_be.domain.requests.job.JobResults;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.global.alert.AlertDeduplicator;
import DGU_AI_LAB.admin_be.domain.requests.service.RequestExpiryService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 컨테이너 회수 작업의 결과를 회수 중(EXPIRING)인 신청에 반영한다. 공통 규칙은 {@link JobResultPoller}.
 *
 * <ul>
 *   <li>SUCCESS → DELETED, 외부 포트 회수, 안내</li>
 *   <li>FAIL → FULFILLED로 되돌리고 관리자에게 알림(다음 만료 회차나 관리자의 재시도가 다시 회수한다)</li>
 *   <li>DEGRADED·UNKNOWN → 컨테이너가 남았는지 모르므로 그대로 두고 관리자에게 한 번 알림</li>
 * </ul>
 */
@Component
public class RevokeJobPoller extends JobResultPoller {

    private final RequestExpiryService requestExpiryService;
    private final AlarmService alarmService;

    public RevokeJobPoller(RequestRepository requestRepository, JobClient jobClient, AlertDeduplicator alertDeduplicator,
                           RequestExpiryService requestExpiryService, AlarmService alarmService) {
        super(requestRepository, jobClient, alertDeduplicator, Status.EXPIRING, JobResults.KIND_REVOKE);
        this.requestExpiryService = requestExpiryService;
        this.alarmService = alarmService;
    }

    @Scheduled(fixedDelayString = "${operations.revoke.poll-ms:3000}")
    public void pollRevokeJobs() {
        pollOnce();
    }

    @Override
    protected void onSuccess(Request request, JobResultResponseDTO result) {
        requestExpiryService.completeContainerRevoke(request.getRequestId());
    }

    @Override
    protected void onFailure(Request request, JobResultResponseDTO result) {
        requestExpiryService.failContainerRevoke(request.getRequestId());
        alert(request, result, "notification.admin.revoke.job-failed");
    }

    @Override
    protected void onUnresolved(Request request, JobResultResponseDTO result) {
        alert(request, result, JobResults.isDegraded(result)
                ? "notification.admin.revoke.job-degraded" : "notification.admin.revoke.job-unknown");
    }

    private void alert(Request request, JobResultResponseDTO result, String messageKey) {
        alarmService.alertNeedsAction(messageKey, request.getRequestId(), request.getPodName(), result.errorCode());
    }
}
