package DGU_AI_LAB.admin_be.domain.scheduler;

import DGU_AI_LAB.admin_be.domain.groups.entity.GroupOperation;
import DGU_AI_LAB.admin_be.domain.groups.entity.GroupOperationStatus;
import DGU_AI_LAB.admin_be.domain.groups.repository.GroupOperationRepository;
import DGU_AI_LAB.admin_be.domain.groups.service.GroupOperationService;
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
class GroupOperationJobPollerTest {

    private static final Long OPERATION_ID = 7L;
    private static final Long JOB_ID = 77L;
    private static final JobResultResponseDTO.Result RESULT = new JobResultResponseDTO.Result(null, 70001L, null, null, null);

    @Mock private GroupOperationRepository operationRepository;
    @Mock private JobClient jobClient;
    @Mock private GroupOperationService groupOperationService;
    @InjectMocks private GroupOperationJobPoller poller;

    private static GroupOperation operation(Long operationId) {
        GroupOperation operation = mock(GroupOperation.class);
        when(operation.getGroupOperationId()).thenReturn(operationId);
        when(operation.getJobId()).thenReturn(JOB_ID);
        return operation;
    }

    private void processing(GroupOperation... operations) {
        when(operationRepository.findAllByStatus(GroupOperationStatus.PROCESSING)).thenReturn(List.of(operations));
    }

    private void result(Long operationId, Long jobId, String phase, String errorCode) {
        when(jobClient.getResult(JobResults.KIND_GROUP, operationId)).thenReturn(new JobResultResponseDTO(
                String.valueOf(operationId), JobResults.KIND_GROUP, jobId, phase, errorCode, null, RESULT));
    }

    @Test
    @DisplayName("작업이 성공하면 결과와 함께 반영한다")
    void successCompletes() {
        processing(operation(OPERATION_ID));
        result(OPERATION_ID, JOB_ID, JobResults.PHASE_SUCCESS, null);

        poller.pollGroupOperations();

        verify(groupOperationService).complete(OPERATION_ID, RESULT);
        verify(groupOperationService, never()).fail(anyLong(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {JobResults.PHASE_START, JobResults.PHASE_RETRY})
    @DisplayName("작업이 도는 중이면 기다린다")
    void runningWaits(String phase) {
        processing(operation(OPERATION_ID));
        result(OPERATION_ID, JOB_ID, phase, null);

        poller.pollGroupOperations();

        verifyNoInteractions(groupOperationService);
    }

    @ParameterizedTest
    @CsvSource({"FAIL,AD_GROUP_MEMBER_FAILED,AD_GROUP_MEMBER_FAILED", "FAIL,DEGRADED,DEGRADED", "UNKNOWN,,UNKNOWN", "none,,none"})
    @DisplayName("그 밖에 끝난 결과는 실패로 닫는다 — 다시 요청하면 이어서 끝나므로 반영 중에 남겨 두지 않는다")
    void finishedWithoutSuccessFails(String phase, String errorCode, String recorded) {
        processing(operation(OPERATION_ID));
        result(OPERATION_ID, JOB_ID, phase, errorCode);

        poller.pollGroupOperations();

        verify(groupOperationService).fail(OPERATION_ID, recorded);
        verify(groupOperationService, never()).complete(anyLong(), any());
    }

    @Test
    @DisplayName("이번에 등록한 작업이 아닌 결과는 반영하지 않는다")
    void resultOfAnotherJobIsIgnored() {
        processing(operation(OPERATION_ID));
        result(OPERATION_ID, 76L, JobResults.PHASE_SUCCESS, null);

        poller.pollGroupOperations();

        verifyNoInteractions(groupOperationService);
    }

    @Test
    @DisplayName("한 작업의 조회 실패가 나머지 작업 처리를 막지 않는다")
    void oneFailureDoesNotBlockTheRest() {
        processing(operation(6L), operation(OPERATION_ID));
        when(jobClient.getResult(JobResults.KIND_GROUP, 6L)).thenThrow(new BusinessException(ErrorCode.EXTERNAL_API_ERROR));
        result(OPERATION_ID, JOB_ID, JobResults.PHASE_SUCCESS, null);

        poller.pollGroupOperations();

        verify(groupOperationService).complete(OPERATION_ID, RESULT);
    }
}
