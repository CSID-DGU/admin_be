package DGU_AI_LAB.admin_be.domain.scheduler;

import DGU_AI_LAB.admin_be.domain.requests.dto.response.JobResultResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.entity.ChangeRequest;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.job.JobClient;
import DGU_AI_LAB.admin_be.domain.requests.job.JobResults;
import DGU_AI_LAB.admin_be.domain.users.dto.response.PasswordResetSummaryDTO;
import DGU_AI_LAB.admin_be.domain.users.entity.PasswordResetRequest;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetJobPollerTest {

    /** 작업 기록의 키. 신청의 상태는 변경 요청 번호로 바꾼다. */
    private static final Long RESET_ID = 12L;
    private static final Long CHANGE_ID = 40L;
    private static final Long JOB_ID = 77L;

    @Mock private PasswordResetRequestRepository resetRepository;
    @Mock private JobClient jobClient;
    @Mock private PasswordResetService passwordResetService;
    @Mock private PasswordResetNotifier notifier;
    @InjectMocks private PasswordResetJobPoller poller;

    private static final PasswordResetSummaryDTO SUMMARY =
            new PasswordResetSummaryDTO(CHANGE_ID, 1L, "홍길동", "test@dgu.ac.kr", "honggildong", "PENDING", null, null);

    private static PasswordResetRequest reset(Long changeRequestId, Long resetId, Long jobId, LocalDateTime approvedAt) {
        ChangeRequest changeRequest = mock(ChangeRequest.class);
        when(changeRequest.getChangeRequestId()).thenReturn(changeRequestId);
        lenient().when(changeRequest.getUpdatedAt()).thenReturn(approvedAt);
        PasswordResetRequest reset = mock(PasswordResetRequest.class);
        when(reset.getChangeRequest()).thenReturn(changeRequest);
        lenient().when(reset.getPasswordResetRequestId()).thenReturn(resetId);
        when(reset.getJobId()).thenReturn(jobId);
        return reset;
    }

    private void processing(Long resetId, Long jobId) {
        PasswordResetRequest reset = reset(CHANGE_ID, resetId, jobId, LocalDateTime.now());
        when(resetRepository.findAllByStatus(Status.PROCESSING)).thenReturn(List.of(reset));
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

        verify(passwordResetService).complete(CHANGE_ID);
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
        when(passwordResetService.returnToPending(CHANGE_ID)).thenReturn(Optional.of(SUMMARY));

        poller.pollPasswordResets();

        verify(passwordResetService, never()).complete(anyLong());
        verify(notifier).jobFailed(SUMMARY, JobResults.PHASE_FAIL, "POD_PASSWORD_SYNC_FAILED", false);
    }

    @Test
    @DisplayName("되돌리지 못한 실패(DEGRADED)는 일부만 바뀌었을 수 있다고 알린다")
    void degradedIsReportedAsPartial() {
        processing(RESET_ID, JOB_ID);
        result(JOB_ID, JobResults.PHASE_FAIL, JobResults.ERROR_DEGRADED);
        when(passwordResetService.returnToPending(CHANGE_ID)).thenReturn(Optional.of(SUMMARY));

        poller.pollPasswordResets();

        verify(notifier).jobFailed(SUMMARY, JobResults.PHASE_FAIL, JobResults.ERROR_DEGRADED, true);
    }

    @ParameterizedTest
    @ValueSource(strings = {JobResults.PHASE_UNKNOWN, JobResults.PHASE_NONE})
    @DisplayName("결과를 모르거나 작업 기록이 없어도 PROCESSING에 남기지 않고 승인 대기로 되돌린다")
    void unknownReturnsToPending(String phase) {
        processing(RESET_ID, JOB_ID);
        result(JobResults.PHASE_NONE.equals(phase) ? null : JOB_ID, phase, null);
        when(passwordResetService.returnToPending(CHANGE_ID)).thenReturn(Optional.of(SUMMARY));

        poller.pollPasswordResets();

        verify(notifier).jobFailed(SUMMARY, phase, null, true);
    }

    @Test
    @DisplayName("이미 다른 스레드가 되돌린 신청이면 또 알리지 않는다")
    void alreadyReturnedIsNotReportedTwice() {
        processing(RESET_ID, JOB_ID);
        result(JOB_ID, JobResults.PHASE_FAIL, "POD_PASSWORD_SYNC_FAILED");
        when(passwordResetService.returnToPending(CHANGE_ID)).thenReturn(Optional.empty());

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

    /** 등록 결과를 확인하지 못해 작업 번호 없이 승인된 신청. */
    private void processingWithoutJob(LocalDateTime approvedAt) {
        // 승인 시각은 변경 요청의 고친 시각이다 — 작업 번호 없이 승인되면 이 행은 바뀌지 않는다.
        PasswordResetRequest reset = reset(CHANGE_ID, RESET_ID, null, approvedAt);
        when(resetRepository.findAllByStatus(Status.PROCESSING)).thenReturn(List.of(reset));
    }

    @Test
    @DisplayName("작업 번호가 없는 신청은 등록 대기 시간 동안 결과를 조회하지 않는다 — 작업이 아직 기록되지 않았을 수 있다")
    void unconfirmedRegistrationWaitsOutGrace() {
        processingWithoutJob(LocalDateTime.now());

        poller.pollPasswordResets();

        verifyNoInteractions(jobClient, passwordResetService, notifier);
    }

    @Test
    @DisplayName("작업 번호가 없는 신청도 등록 대기 시간이 지나 작업이 성공해 있으면 적용한다")
    void unconfirmedRegistrationCompletesAfterGrace() {
        processingWithoutJob(LocalDateTime.now().minus(JobResults.REGISTRATION_GRACE).minusSeconds(1));
        result(JOB_ID, JobResults.PHASE_SUCCESS, null);

        poller.pollPasswordResets();

        verify(passwordResetService).complete(CHANGE_ID);
        verifyNoInteractions(notifier);
    }

    @Test
    @DisplayName("작업 번호가 없는 신청은 등록 대기 시간이 지나도 작업 기록이 없으면 승인 대기로 되돌린다")
    void unconfirmedRegistrationWithoutJobReturnsToPending() {
        processingWithoutJob(LocalDateTime.now().minus(JobResults.REGISTRATION_GRACE).minusSeconds(1));
        result(null, JobResults.PHASE_NONE, null);
        when(passwordResetService.returnToPending(CHANGE_ID)).thenReturn(Optional.of(SUMMARY));

        poller.pollPasswordResets();

        verify(notifier).jobFailed(SUMMARY, JobResults.PHASE_NONE, null, true);
    }

    @Test
    @DisplayName("한 신청의 조회 실패가 나머지 신청 처리를 막지 않는다")
    void oneFailureDoesNotStopOthers() {
        PasswordResetRequest broken = reset(39L, 11L, JOB_ID, LocalDateTime.now());
        PasswordResetRequest fine = reset(CHANGE_ID, RESET_ID, JOB_ID, LocalDateTime.now());
        when(resetRepository.findAllByStatus(Status.PROCESSING)).thenReturn(List.of(broken, fine));
        when(jobClient.getResult(JobResults.KIND_PASSWORD, 11L)).thenThrow(new BusinessException(ErrorCode.EXTERNAL_API_ERROR));
        result(JOB_ID, JobResults.PHASE_SUCCESS, null);

        poller.pollPasswordResets();

        verify(passwordResetService).complete(CHANGE_ID);
        verify(notifier, never()).jobFailed(any(), any(), any(), anyBoolean());
    }
}
