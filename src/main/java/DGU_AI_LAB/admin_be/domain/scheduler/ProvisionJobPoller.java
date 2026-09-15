package DGU_AI_LAB.admin_be.domain.scheduler;

import DGU_AI_LAB.admin_be.domain.requests.dto.response.JobResultResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.requests.service.AdminRequestCommandService;
import DGU_AI_LAB.admin_be.domain.requests.service.OperationJobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 승인 때 등록한 생성 작업의 결과를 받아 신청에 반영한다.
 *
 * <p>승인 API가 작업만 등록하고 바로 돌아오므로, 계정·컨테이너가 실제로 만들어졌는지는 이 폴러가 확인한다.
 * 대상은 처리 중(PROCESSING)인 신청 전부다 — 작업이 아직 끝나지 않았으면(START) 다음 바퀴에 다시 보고,
 * 등록 이력이 없으면(none) 건드리지 않는다(오래 방치되면 RequestSchedulerService 재조정이 되돌린다).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProvisionJobPoller {

    private final RequestRepository requestRepository;
    private final OperationJobService operationJobService;
    private final AdminRequestCommandService adminRequestCommandService;

    // 결과 불명은 신청 상태를 그대로 두므로 다음 바퀴에도 계속 잡힌다. 같은 신청으로 관리자에게
    // 같은 알림이 반복해서 가지 않도록 한 번 알린 신청을 기억한다.
    private final Set<Long> reportedUnknown = ConcurrentHashMap.newKeySet();

    @Scheduled(fixedDelayString = "${operations.provision.poll-ms:3000}")
    public void pollProvisionJobs() {
        List<Request> processing = requestRepository.findAllByStatus(Status.PROCESSING);
        for (Request request : processing) {
            Long requestId = request.getRequestId();
            try {
                handle(requestId, operationJobService.getResult(OperationJobService.KIND_PROVISION, requestId));
            } catch (Exception e) {
                // 한 신청의 조회 실패가 나머지 신청 처리를 막지 않게 한다. 다음 바퀴에 다시 조회한다.
                log.warn("생성 작업 결과 조회 실패 - requestId={}", requestId, e);
            }
        }
    }

    private void handle(Long requestId, JobResultResponseDTO result) {
        switch (result.phase()) {
            case OperationJobService.PHASE_SUCCESS -> {
                reportedUnknown.remove(requestId);
                adminRequestCommandService.completeApprovalJob(requestId, result.result());
            }
            case OperationJobService.PHASE_FAIL -> {
                if (OperationJobService.isDegraded(result)) {
                    // 제어기가 자원을 남긴 채 넘긴 실패다. 되돌리면 컨테이너는 살아 있는데 신청만 PENDING이 되고,
                    // 재승인 때 컨테이너가 하나 더 만들어진다. 결과 불명과 같이 신청을 그대로 두고 한 번만 알린다.
                    if (reportedUnknown.add(requestId)) {
                        adminRequestCommandService.reportDegradedApprovalJob(requestId, result);
                    }
                    return;
                }
                reportedUnknown.remove(requestId);
                adminRequestCommandService.failApprovalJob(requestId, result);
            }
            case OperationJobService.PHASE_UNKNOWN -> {
                if (reportedUnknown.add(requestId)) {
                    adminRequestCommandService.reportUnknownApprovalJob(requestId, result);
                }
            }
            default -> {
                // START: 아직 실행 중. none: 등록 이력 없음.
            }
        }
    }
}
