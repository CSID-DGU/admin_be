package DGU_AI_LAB.admin_be.domain.scheduler;

import DGU_AI_LAB.admin_be.domain.alarm.service.AlarmService;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.JobResultResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.requests.service.OperationJobService;
import DGU_AI_LAB.admin_be.domain.requests.service.RequestExpiryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RevokeJobPoller")
class RevokeJobPollerTest {

    private static final long REQUEST_ID = 41L;
    private static final long JOB_ID = 900L;

    @Mock private RequestRepository requestRepository;
    @Mock private OperationJobService operationJobService;
    @Mock private RequestExpiryService requestExpiryService;
    @Mock private AlarmService alarmService;

    @InjectMocks private RevokeJobPoller poller;

    private Request expiring;

    @BeforeEach
    void setUp() {
        expiring = mock(Request.class);
        when(expiring.getRequestId()).thenReturn(REQUEST_ID);
        when(expiring.getStatus()).thenReturn(Status.EXPIRING);
        when(expiring.getJobId()).thenReturn(JOB_ID);
        when(expiring.getPodName()).thenReturn("pod-a");
        when(expiring.getUpdatedAt()).thenReturn(LocalDateTime.now().minusMinutes(5));
        when(requestRepository.findAllByStatus(Status.EXPIRING)).thenReturn(List.of(expiring));
    }

    private void givenResult(String phase, Long jobId, String errorCode) {
        when(operationJobService.getResult(OperationJobService.KIND_REVOKE, REQUEST_ID)).thenReturn(
                new JobResultResponseDTO(String.valueOf(REQUEST_ID), OperationJobService.KIND_REVOKE, jobId, phase, errorCode, null, null));
    }

    @Test
    @DisplayName("성공하면 신청을 DELETED로 마무리한다")
    void successCompletes() {
        givenResult(OperationJobService.PHASE_SUCCESS, JOB_ID, null);

        poller.pollRevokeJobs();

        verify(requestExpiryService).completeContainerRevoke(REQUEST_ID);
        verify(requestExpiryService, never()).failContainerRevoke(anyLong());
    }

    @Test
    @DisplayName("실패하면 FULFILLED로 되돌리고 관리자에게 알린다")
    void failureRevertsAndAlerts() {
        givenResult(OperationJobService.PHASE_FAIL, JOB_ID, "POD_DELETE_FAILED");

        poller.pollRevokeJobs();

        verify(requestExpiryService).failContainerRevoke(REQUEST_ID);
        verify(alarmService).sendSlackAlert(contains("POD_DELETE_FAILED"), isNull());
    }

    @Test
    @DisplayName("자원을 남긴 실패(DEGRADED)와 결과 불명은 되돌리지 않고 한 번만 알린다")
    void degradedAndUnknownAlertOnce() {
        givenResult(OperationJobService.PHASE_FAIL, JOB_ID, OperationJobService.ERROR_DEGRADED);
        poller.pollRevokeJobs();
        poller.pollRevokeJobs();

        givenResult(OperationJobService.PHASE_UNKNOWN, JOB_ID, "TIMEOUT");
        poller.pollRevokeJobs();

        verify(requestExpiryService, never()).failContainerRevoke(anyLong());
        verify(requestExpiryService, never()).completeContainerRevoke(anyLong());
        verify(alarmService, times(1)).sendSlackAlert(anyString(), isNull());
    }

    @Test
    @DisplayName("실행 중이면 아무것도 하지 않는다")
    void startWaits() {
        givenResult(OperationJobService.PHASE_START, JOB_ID, null);

        poller.pollRevokeJobs();

        verifyNoInteractions(requestExpiryService, alarmService);
    }

    @Test
    @DisplayName("다른 작업(이전 회수 시도)의 결과는 반영하지 않는다")
    void ignoresOtherJob() {
        givenResult(OperationJobService.PHASE_FAIL, 111L, "OLD_FAILURE");

        poller.pollRevokeJobs();

        verifyNoInteractions(requestExpiryService, alarmService);
    }

    @Test
    @DisplayName("방금 선점돼 작업 번호가 아직 없으면 조회하지 않는다 — 이전 작업의 결과가 보인다")
    void awaitingRegistrationIsSkipped() {
        when(expiring.getJobId()).thenReturn(null);
        when(expiring.getUpdatedAt()).thenReturn(LocalDateTime.now());

        poller.pollRevokeJobs();

        verify(operationJobService, never()).getResult(any(), any());
    }

    @Test
    @DisplayName("한 신청의 조회 실패가 다른 신청 처리를 막지 않는다")
    void lookupFailureIsIsolated() {
        Request other = mock(Request.class);
        when(other.getRequestId()).thenReturn(42L);
        when(other.getJobId()).thenReturn(901L);
        when(requestRepository.findAllByStatus(Status.EXPIRING)).thenReturn(List.of(expiring, other));
        when(operationJobService.getResult(OperationJobService.KIND_REVOKE, REQUEST_ID)).thenThrow(new RuntimeException("reset"));
        when(operationJobService.getResult(OperationJobService.KIND_REVOKE, 42L)).thenReturn(
                new JobResultResponseDTO("42", OperationJobService.KIND_REVOKE, 901L, OperationJobService.PHASE_SUCCESS, null, null, null));

        poller.pollRevokeJobs();

        verify(requestExpiryService).completeContainerRevoke(42L);
    }
}
