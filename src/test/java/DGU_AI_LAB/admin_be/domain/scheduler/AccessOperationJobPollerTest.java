package DGU_AI_LAB.admin_be.domain.scheduler;

import DGU_AI_LAB.admin_be.domain.requests.dto.response.JobResultResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.job.JobClient;
import DGU_AI_LAB.admin_be.domain.requests.job.JobResults;
import DGU_AI_LAB.admin_be.domain.warnings.entity.AccessOperation;
import DGU_AI_LAB.admin_be.domain.warnings.entity.AccessOperationStatus;
import DGU_AI_LAB.admin_be.domain.warnings.repository.AccessOperationRepository;
import DGU_AI_LAB.admin_be.domain.warnings.service.AccessEnforcementService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccessOperationJobPollerTest {

    private static final Long OPERATION_ID = 7L;
    private static final Long JOB_ID = 77L;

    @Mock private AccessOperationRepository operationRepository;
    @Mock private JobClient jobClient;
    @Mock private AccessEnforcementService accessEnforcementService;
    @InjectMocks private AccessOperationJobPoller poller;

    private static AccessOperation operation(String clusterId) {
        AccessOperation operation = mock(AccessOperation.class);
        when(operation.getAccessOperationId()).thenReturn(OPERATION_ID);
        when(operation.getClusterId()).thenReturn(clusterId);
        when(operation.getJobId()).thenReturn(JOB_ID);
        return operation;
    }

    private void result(String clusterId, String phase, String errorCode) {
        when(jobClient.getResult(clusterId, JobResults.KIND_ACCESS, OPERATION_ID))
                .thenReturn(new JobResultResponseDTO(
                        String.valueOf(OPERATION_ID), JobResults.KIND_ACCESS, JOB_ID,
                        phase, errorCode, null, null));
    }

    @Test
    @DisplayName("LAB 접속 작업은 FARM이 아니라 저장된 LAB Config Server에서 결과를 조회한다")
    void labOperationUsesStoredCluster() {
        AccessOperation operation = operation("LAB");
        when(operationRepository.findAllByStatus(AccessOperationStatus.PROCESSING))
                .thenReturn(List.of(operation));
        result("LAB", JobResults.PHASE_SUCCESS, null);

        poller.pollAccessOperations();

        verify(jobClient).getResult("LAB", JobResults.KIND_ACCESS, OPERATION_ID);
        verify(accessEnforcementService).complete(OPERATION_ID);
        verify(accessEnforcementService, never()).fail(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("끝난 실패는 해당 클러스터 작업 행을 FAILED로 닫는다")
    void failedOperationIsClosed() {
        AccessOperation operation = operation("FARM");
        when(operationRepository.findAllByStatus(AccessOperationStatus.PROCESSING))
                .thenReturn(List.of(operation));
        result("FARM", JobResults.PHASE_FAIL, "ACCESS_UPDATE_FAILED");

        poller.pollAccessOperations();

        verify(accessEnforcementService).fail(OPERATION_ID, "ACCESS_UPDATE_FAILED");
        verify(accessEnforcementService, never()).complete(OPERATION_ID);
    }
}
