package DGU_AI_LAB.admin_be.domain.scheduler;

import DGU_AI_LAB.admin_be.domain.groups.entity.GroupOperation;
import DGU_AI_LAB.admin_be.domain.groups.entity.GroupOperationStatus;
import DGU_AI_LAB.admin_be.domain.groups.repository.GroupOperationRepository;
import DGU_AI_LAB.admin_be.domain.groups.service.GroupOperationService;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.JobResultResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.job.JobClient;
import DGU_AI_LAB.admin_be.domain.requests.job.JobResults;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 반영 중(PROCESSING)인 공용 그룹 작업마다 작업 결과를 조회해 반영한다.
 *
 * <ul>
 *   <li>SUCCESS → DB 반영(APPLIED)</li>
 *   <li>그 밖에 끝난 결과(FAIL·UNKNOWN·기록 없음) → FAILED. 그룹 추가는 변경 요청을 승인 대기로 되돌린다</li>
 *   <li>START·RETRY → 다음 바퀴에 다시 본다</li>
 *   <li>이번에 등록한 작업이 아닌 결과는 반영하지 않는다</li>
 * </ul>
 *
 * <p>컨테이너 작업과 달리 결과 불명·일부만 반영된 실패도 PROCESSING 에 남겨 두지 않는다. 그룹 작업은 이미 맞춰진
 * 조각을 그대로 두고 나머지만 맞추므로, 같은 요청을 다시 내면 어느 상태에서든 이어서 끝나기 때문이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GroupOperationJobPoller {

    private final GroupOperationRepository operationRepository;
    private final JobClient jobClient;
    private final GroupOperationService groupOperationService;

    @Scheduled(fixedDelayString = "${operations.group.poll-ms:3000}")
    public void pollGroupOperations() {
        for (GroupOperation operation : operationRepository.findAllByStatus(GroupOperationStatus.PROCESSING)) {
            Long operationId = operation.getGroupOperationId();
            try {
                advance(operationId, operation.getJobId());
            } catch (Exception e) {
                // 한 작업의 실패가 나머지 작업 처리를 막지 않게 한다. 조회 실패는 대개 일시적이라 다음 바퀴에 다시 본다.
                log.warn("[groupOperation] 작업 결과 반영 실패 - 다음 바퀴에 다시 본다: operationId={}", operationId, e);
            }
        }
    }

    private void advance(Long operationId, Long jobId) {
        JobResultResponseDTO result = jobClient.getResult(JobResults.KIND_GROUP, operationId);
        if (JobResults.isFromOtherJob(jobId, result) || JobResults.isRunning(result.phase())) {
            return;
        }
        if (JobResults.PHASE_SUCCESS.equals(result.phase())) {
            groupOperationService.complete(operationId, result.result());
            return;
        }
        groupOperationService.fail(operationId, result.errorCode() != null ? result.errorCode() : result.phase());
    }
}
