package DGU_AI_LAB.admin_be.domain.scheduler;

import DGU_AI_LAB.admin_be.domain.requests.dto.response.JobResultResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.job.JobClient;
import DGU_AI_LAB.admin_be.domain.requests.job.JobResults;
import DGU_AI_LAB.admin_be.domain.users.dto.response.PasswordResetSummaryDTO;
import DGU_AI_LAB.admin_be.domain.users.entity.PasswordResetRequest;
import DGU_AI_LAB.admin_be.domain.users.entity.PasswordResetStatus;
import DGU_AI_LAB.admin_be.domain.users.repository.PasswordResetRequestRepository;
import DGU_AI_LAB.admin_be.domain.users.service.PasswordResetNotifier;
import DGU_AI_LAB.admin_be.domain.users.service.PasswordResetService;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetJobPollerTest {

    private static final Long RESET_ID = 12L;
    private static final Long JOB_ID = 77L;

    @Mock private PasswordResetRequestRepository resetRepository;
    @Mock private JobClient jobClient;
    @Mock private PasswordResetService passwordResetService;
    @Mock private PasswordResetNotifier notifier;
    @InjectMocks private PasswordResetJobPoller poller;

    private static final PasswordResetSummaryDTO SUMMARY =
            new PasswordResetSummaryDTO(RESET_ID, 1L, "홍길동", "test@dgu.ac.kr", "honggildong", "PENDING", null, null);

    private void processing(Long resetId, Long jobId) {
        PasswordResetRequest reset = mock(PasswordResetRequest.class);
        when(reset.getPasswordResetRequestId()).thenReturn(resetId);
        when(reset.getJobId()).thenReturn(jobId);
        when(resetRepository.findAllByStatus(PasswordResetStatus.PROCESSING)).thenReturn(List.of(reset));
    }

    private void result(Long jobId, String phase, String errorCode) {
        when(jobClient.getResult(JobResults.KIND_PASSWORD, RESET_ID)).thenReturn(new JobResultResponseDTO(
                String.valueOf(RESET_ID), JobResults.KIND_PASSWORD, jobId, phase, errorCode, null, null));
    }

    @Test
    @DisplayName("작업이 성공하면 신청을 적용한다")
    void successCompletes() {
        processing(RESET_ID, JOB_ID);
        result(JOB_ID, JobResults.PHASE_SUCCESS, null);

        poller.pollPasswordResets();

        verify(passwordResetService).complete(RESET_ID);
        verify(passwordResetService, never()).returnToPending(anyLong());
        verifyNoInteractions(notifier);
    }

    @ParameterizedTest
    @ValueSource(strings = {JobResults.PHASE_START, JobResults.PHASE_RETRY})
    @DisplayName("작업이 도는 중이면 기다린다")
    void runningWaits(String phase) {
        processing(RESET_ID, JOB_ID);
        result(JOB_ID, phase, null);

        poller.pollPasswordResets();

        verifyNoInteractions(passwordResetService, notifier);
    }

    @Test
    @DisplayName("되돌린 뒤 끝난 실패는 승인 대기로 되돌리고, 다시 승인하면 된다고 알린다")
    void cleanFailureReturnsToPending() {
        processing(RESET_ID, JOB_ID);
        result(JOB_ID, JobResults.PHASE_FAIL, "POD_PASSWORD_SYNC_FAILED");
        when(passwordResetService.returnToPending(RESET_ID)).thenReturn(Optional.of(SUMMARY));

        poller.pollPasswordResets();

        verify(passwordResetService, never()).complete(anyLong());
        verify(notifier).jobFailed(SUMMARY, JobResults.PHASE_FAIL, "POD_PASSWORD_SYNC_FAILED", false);
    }

    @Test
    @DisplayName("되돌리지 못한 실패(DEGRADED)는 일부만 바뀌었을 수 있다고 알린다")
    void degradedIsReportedAsPartial() {
        processing(RESET_ID, JOB_ID);
        result(JOB_ID, JobResults.PHASE_FAIL, JobResults.ERROR_DEGRADED);
        when(passwordResetService.returnToPending(RESET_ID)).thenReturn(Optional.of(SUMMARY));

        poller.pollPasswordResets();

        verify(notifier).jobFailed(SUMMARY, JobResults.PHASE_FAIL, JobResults.ERROR_DEGRADED, true);
    }

    @ParameterizedTest
    @ValueSource(strings = {JobResults.PHASE_UNKNOWN, JobResults.PHASE_NONE})
    @DisplayName("결과를 모르거나 작업 기록이 없어도 PROCESSING에 남기지 않고 승인 대기로 되돌린다")
    void unknownReturnsToPending(String phase) {
        processing(RESET_ID, JOB_ID);
        result(JobResults.PHASE_NONE.equals(phase) ? null : JOB_ID, phase, null);
        when(passwordResetService.returnToPending(RESET_ID)).thenReturn(Optional.of(SUMMARY));

        poller.pollPasswordResets();

        verify(notifier).jobFailed(SUMMARY, phase, null, true);
    }

    @Test
    @DisplayName("이미 다른 스레드가 되돌린 신청이면 또 알리지 않는다")
    void alreadyReturnedIsNotReportedTwice() {
        processing(RESET_ID, JOB_ID);
        result(JOB_ID, JobResults.PHASE_FAIL, "POD_PASSWORD_SYNC_FAILED");
        when(passwordResetService.returnToPending(RESET_ID)).thenReturn(Optional.empty());

        poller.pollPasswordResets();

        verifyNoInteractions(notifier);
    }

    @Test
    @DisplayName("이번에 등록한 작업이 아닌 결과는 반영하지 않는다 — 다시 승인한 직후 앞선 작업의 실패가 보일 수 있다")
    void otherJobResultIsIgnored() {
        processing(RESET_ID, JOB_ID);
        result(76L, JobResults.PHASE_FAIL, "POD_PASSWORD_SYNC_FAILED");

        poller.pollPasswordResets();

        verifyNoInteractions(passwordResetService, notifier);
    }

    @Test
    @DisplayName("한 신청의 조회 실패가 나머지 신청 처리를 막지 않는다")
    void oneFailureDoesNotStopOthers() {
        PasswordResetRequest broken = mock(PasswordResetRequest.class);
        when(broken.getPasswordResetRequestId()).thenReturn(11L);
        PasswordResetRequest fine = mock(PasswordResetRequest.class);
        when(fine.getPasswordResetRequestId()).thenReturn(RESET_ID);
        when(fine.getJobId()).thenReturn(JOB_ID);
        when(resetRepository.findAllByStatus(PasswordResetStatus.PROCESSING)).thenReturn(List.of(broken, fine));
        when(jobClient.getResult(JobResults.KIND_PASSWORD, 11L)).thenThrow(new BusinessException(ErrorCode.EXTERNAL_API_ERROR));
        result(JOB_ID, JobResults.PHASE_SUCCESS, null);

        poller.pollPasswordResets();

        verify(passwordResetService).complete(RESET_ID);
        verify(notifier, never()).jobFailed(any(), any(), any(), anyBoolean());
    }
}
