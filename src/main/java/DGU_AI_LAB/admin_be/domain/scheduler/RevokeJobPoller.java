package DGU_AI_LAB.admin_be.domain.scheduler;

import DGU_AI_LAB.admin_be.domain.alarm.service.AlarmService;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.JobResultResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.requests.service.OperationJobService;
import DGU_AI_LAB.admin_be.domain.requests.service.RequestExpiryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 컨테이너 회수 작업의 결과를 받아 신청에 반영한다. 대상은 회수 중(EXPIRING)인 신청 전부다.
 *
 * <ul>
 *   <li>SUCCESS → DELETED, 외부 포트 회수, 안내</li>
 *   <li>FAIL → FULFILLED로 되돌리고 관리자에게 알림(다음 만료 회차나 관리자의 재시도가 다시 회수한다)</li>
 *   <li>DEGRADED·UNKNOWN → 컨테이너가 남았는지 모르므로 그대로 두고 관리자에게 한 번 알림</li>
 *   <li>START·none → 다음 바퀴에 다시 본다(등록 없이 오래 방치되면 RequestSchedulerService 재조정이 되돌린다)</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RevokeJobPoller {

    private final RequestRepository requestRepository;
    private final OperationJobService operationJobService;
    private final RequestExpiryService requestExpiryService;
    private final AlarmService alarmService;

    // 결과 불명은 신청 상태를 그대로 두므로 다음 바퀴에도 계속 잡힌다. 같은 알림이 반복되지 않도록 한 번 알린 신청을 기억한다.
    private final Set<Long> reportedUnknown = ConcurrentHashMap.newKeySet();

    @Scheduled(fixedDelayString = "${operations.revoke.poll-ms:3000}")
    public void pollRevokeJobs() {
        for (Request request : requestRepository.findAllByStatus(Status.EXPIRING)) {
            Long requestId = request.getRequestId();
            if (OperationJobService.awaitingRegistration(request.getJobId(), request.getUpdatedAt())) {
                continue;
            }
            try {
                JobResultResponseDTO result = operationJobService.getResult(OperationJobService.KIND_REVOKE, requestId);
                if (OperationJobService.isFromOtherJob(request.getJobId(), result)) {
                    // 다시 회수를 시작한 직후 보이는 이전 회수 작업의 결과다.
                    continue;
                }
                handle(request, result);
            } catch (Exception e) {
                // 한 신청의 조회 실패가 나머지 신청 처리를 막지 않게 한다. 다음 바퀴에 다시 조회한다.
                log.warn("회수 작업 결과 조회 실패 - requestId={}", requestId, e);
            }
        }
    }

    private void handle(Request request, JobResultResponseDTO result) {
        Long requestId = request.getRequestId();
        switch (result.phase()) {
            case OperationJobService.PHASE_SUCCESS -> {
                reportedUnknown.remove(requestId);
                requestExpiryService.completeContainerRevoke(requestId);
            }
            case OperationJobService.PHASE_FAIL -> {
                if (OperationJobService.isDegraded(result)) {
                    reportOnce(request, result, "컨테이너 회수가 자원을 남긴 채 멈춤");
                    return;
                }
                reportedUnknown.remove(requestId);
                requestExpiryService.failContainerRevoke(requestId);
                alert(request, result, "컨테이너 회수 실패 — FULFILLED로 되돌림");
            }
            case OperationJobService.PHASE_UNKNOWN -> reportOnce(request, result, "컨테이너 회수 결과 불명");
            default -> {
                // START: 아직 실행 중. none: 등록 이력 없음.
            }
        }
    }

    private void reportOnce(Request request, JobResultResponseDTO result, String what) {
        if (reportedUnknown.add(request.getRequestId())) {
            alert(request, result, what + " — 수동 확인 필요");
        }
    }

    private void alert(Request request, JobResultResponseDTO result, String what) {
        log.error("[회수] {}: requestId={}, pod={}, error={}", what, request.getRequestId(), request.getPodName(), result.errorCode());
        try {
            alarmService.sendSlackAlert(String.format("[회수] %s: requestId=%d, pod=%s, error=%s",
                    what, request.getRequestId(), request.getPodName(), result.errorCode()), null);
        } catch (Exception e) {
            log.warn("회수 실패 알림 전송 실패: requestId={}", request.getRequestId(), e);
        }
    }
}
