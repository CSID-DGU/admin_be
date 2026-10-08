package DGU_AI_LAB.admin_be.domain.scheduler;

import DGU_AI_LAB.admin_be.domain.portRequests.entity.PortOperation;
import DGU_AI_LAB.admin_be.domain.portRequests.entity.PortOperationStatus;
import DGU_AI_LAB.admin_be.domain.portRequests.repository.PortOperationRepository;
import DGU_AI_LAB.admin_be.domain.portRequests.service.PortOperationService;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.JobResultResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.job.JobClient;
import DGU_AI_LAB.admin_be.domain.requests.job.JobResults;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortOperationJobPollerTest {

    private static final Long OPERATION_ID = 7L;
    private static final Long JOB_ID = 77L;
    private static final JobResultResponseDTO.Result RESULT =
            new JobResultResponseDTO.Result(null, null, "ailab-alice-1", "farm1", List.of());

    @Mock private PortOperationRepository operationRepository;
    @Mock private JobClient jobClient;
    @Mock private PortOperationService portOperationService;
    @InjectMocks private PortOperationJobPoller poller;

    private static PortOperation operation(Long operationId) {
        PortOperation operation = mock(PortOperation.class);
        when(operation.getPortOperationId()).thenReturn(operationId);
        when(operation.getJobId()).thenReturn(JOB_ID);
        return operation;
    }

    private void processing(PortOperation... operations) {
        when(operationRepository.findAllByStatus(PortOperationStatus.PROCESSING)).thenReturn(List.of(operations));
    }

    private void result(Long operationId, Long jobId, String phase, String errorCode) {
        when(jobClient.getResult(JobResults.KIND_PORT, operationId)).thenReturn(new JobResultResponseDTO(
                String.valueOf(operationId), JobResults.KIND_PORT, jobId, phase, errorCode, null, RESULT));
    }

    @Test
    @DisplayName("작업이 성공하면 결과와 함께 반영한다")
    void successCompletes() {
        processing(operation(OPERATION_ID));
        result(OPERATION_ID, JOB_ID, JobResults.PHASE_SUCCESS, null);

        poller.pollPortOperations();

        verify(portOperationService).complete(OPERATION_ID, RESULT);
        verify(portOperationService, never()).fail(anyLong(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {JobResults.PHASE_START, JobResults.PHASE_RETRY})
    @DisplayName("작업이 도는 중이면 기다린다")
    void runningWaits(String phase) {
        processing(operation(OPERATION_ID));
        result(OPERATION_ID, JOB_ID, phase, null);

        poller.pollPortOperations();

        verifyNoInteractions(portOperationService);
    }

    @ParameterizedTest
    @CsvSource({"FAIL,POD_NOT_FOUND,POD_NOT_FOUND", "FAIL,DEGRADED,DEGRADED", "UNKNOWN,,UNKNOWN", "none,,none"})
    @DisplayName("그 밖에 끝난 결과는 실패로 닫는다 — 다시 승인하면 이어서 끝나므로 반영 중에 남겨 두지 않는다")
    void finishedWithoutSuccessFails(String phase, String errorCode, String recorded) {
        processing(operation(OPERATION_ID));
        result(OPERATION_ID, JOB_ID, phase, errorCode);

        poller.pollPortOperations();

        verify(portOperationService).fail(OPERATION_ID, recorded);
        verify(portOperationService, never()).complete(anyLong(), any());
    }

    @Test
    @DisplayName("이번에 등록한 작업이 아닌 결과는 반영하지 않는다")
    void resultOfAnotherJobIsIgnored() {
        processing(operation(OPERATION_ID));
        result(OPERATION_ID, 76L, JobResults.PHASE_SUCCESS, null);

        poller.pollPortOperations();

        verifyNoInteractions(portOperationService);
    }

    @Test
    @DisplayName("한 작업의 조회 실패가 나머지 작업 처리를 막지 않는다")
    void oneFailureDoesNotBlockTheRest() {
        processing(operation(6L), operation(OPERATION_ID));
        when(jobClient.getResult(JobResults.KIND_PORT, 6L)).thenThrow(new BusinessException(ErrorCode.EXTERNAL_API_ERROR));
        result(OPERATION_ID, JOB_ID, JobResults.PHASE_SUCCESS, null);

        poller.pollPortOperations();

        verify(portOperationService).complete(OPERATION_ID, RESULT);
    }
}
