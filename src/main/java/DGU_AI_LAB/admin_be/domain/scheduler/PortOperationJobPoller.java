package DGU_AI_LAB.admin_be.domain.scheduler;

import DGU_AI_LAB.admin_be.domain.portRequests.entity.PortOperation;
import DGU_AI_LAB.admin_be.domain.portRequests.entity.PortOperationStatus;
import DGU_AI_LAB.admin_be.domain.portRequests.repository.PortOperationRepository;
import DGU_AI_LAB.admin_be.domain.portRequests.service.PortOperationService;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.JobResultResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.job.JobClient;
import DGU_AI_LAB.admin_be.domain.requests.job.JobResults;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 반영 중(PROCESSING)인 추가 포트 변경 작업마다 작업 결과를 조회해 반영한다.
 *
 * <ul>
 *   <li>SUCCESS → DB 반영(APPLIED)</li>
 *   <li>그 밖에 끝난 결과(FAIL·UNKNOWN·기록 없음) → FAILED, 변경 요청을 승인 대기로 되돌린다</li>
 *   <li>START·RETRY → 다음 바퀴에 다시 본다</li>
 *   <li>이번에 등록한 작업이 아닌 결과는 반영하지 않는다</li>
 * </ul>
 *
 * <p>컨테이너 작업과 달리 결과 불명·일부만 반영된 실패도 PROCESSING 에 남겨 두지 않는다. 포트 작업은 이미 맞춰진
 * 포트를 그대로 두고 나머지만 맞추므로, 같은 요청을 다시 승인하면 어느 상태에서든 이어서 끝나기 때문이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PortOperationJobPoller {

    private final PortOperationRepository operationRepository;
    private final JobClient jobClient;
    private final PortOperationService portOperationService;

    @Scheduled(fixedDelayString = "${operations.port.poll-ms:3000}")
    public void pollPortOperations() {
        for (PortOperation operation : operationRepository.findAllByStatus(PortOperationStatus.PROCESSING)) {
            Long operationId = operation.getPortOperationId();
            try {
                advance(operationId, operation.getJobId());
            } catch (Exception e) {
                // 한 작업의 실패가 나머지 작업 처리를 막지 않게 한다. 조회 실패는 대개 일시적이라 다음 바퀴에 다시 본다.
                log.warn("[portOperation] 작업 결과 반영 실패 - 다음 바퀴에 다시 본다: operationId={}", operationId, e);
            }
        }
    }

    private void advance(Long operationId, Long jobId) {
        JobResultResponseDTO result = jobClient.getResult(JobResults.KIND_PORT, operationId);
        if (JobResults.isFromOtherJob(jobId, result) || JobResults.isRunning(result.phase())) {
            return;
        }
        if (JobResults.PHASE_SUCCESS.equals(result.phase())) {
            portOperationService.complete(operationId, result.result());
            return;
        }
        portOperationService.fail(operationId, result.errorCode() != null ? result.errorCode() : result.phase());
    }
}
