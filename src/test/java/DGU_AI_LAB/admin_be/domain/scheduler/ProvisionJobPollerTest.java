package DGU_AI_LAB.admin_be.domain.scheduler;

import DGU_AI_LAB.admin_be.domain.requests.job.JobClient;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.JobResultResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.requests.service.AdminRequestCommandService;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ProvisionJobPoller")
class ProvisionJobPollerTest {

    @Mock private RequestRepository requestRepository;
    @Mock private JobClient jobClient;
    @Mock private AdminRequestCommandService adminRequestCommandService;

    private ProvisionJobPoller poller;

    @BeforeEach
    void setUp() {
        poller = new ProvisionJobPoller(requestRepository, jobClient, adminRequestCommandService);
    }

    private void givenProcessing(Long... requestIds) {
        List<Request> requests = java.util.Arrays.stream(requestIds).map(id -> {
            Request request = mock(Request.class);
            when(request.getRequestId()).thenReturn(id);
            when(request.getJobId()).thenReturn(1L); // result()의 작업 번호와 같다
            return request;
        }).toList();
        when(requestRepository.findAllByStatus(Status.PROCESSING)).thenReturn(requests);
    }

    private Request processingWithJob(Long requestId, Long jobId, java.time.LocalDateTime updatedAt) {
        Request request = mock(Request.class);
        when(request.getRequestId()).thenReturn(requestId);
        when(request.getJobId()).thenReturn(jobId);
        when(request.getUpdatedAt()).thenReturn(updatedAt);
        when(requestRepository.findAllByStatus(Status.PROCESSING)).thenReturn(List.of(request));
        return request;
    }

    private static JobResultResponseDTO jobResult(Long requestId, Long jobId, String phase) {
        return new JobResultResponseDTO(String.valueOf(requestId), "provision", jobId, phase, null, null, null);
    }

    @Test
    @DisplayName("재승인 직후 보이는 이전 작업의 실패는 반영하지 않는다")
    void ignoresResultOfPreviousJob() {
        processingWithJob(39L, 3616L, java.time.LocalDateTime.now());
        when(jobClient.getResult("provision", 39L)).thenReturn(jobResult(39L, 3612L, "FAIL"));

        poller.pollProvisionJobs();

        verify(adminRequestCommandService, never()).failApprovalJob(anyLong(), any());
    }

    @Test
    @DisplayName("등록한 작업의 결과면 반영한다")
    void handlesResultOfRegisteredJob() {
        processingWithJob(39L, 3616L, java.time.LocalDateTime.now());
        JobResultResponseDTO failed = jobResult(39L, 3616L, "FAIL");
        when(jobClient.getResult("provision", 39L)).thenReturn(failed);

        poller.pollProvisionJobs();

        verify(adminRequestCommandService).failApprovalJob(39L, failed);
    }

    @Test
    @DisplayName("작업 번호가 아직 없고 방금 승인된 신청은 등록 중이라 조회하지 않는다")
    void waitsWhileRegistrationInFlight() {
        processingWithJob(39L, null, java.time.LocalDateTime.now());

        poller.pollProvisionJobs();

        verify(jobClient, never()).getResult(anyString(), anyLong());
    }

    @Test
    @DisplayName("작업 번호 없이 오래된 신청(기록 전 중단·배포 전 승인)은 최신 결과를 그대로 쓴다")
    void fallsBackToLatestResultAfterGrace() {
        processingWithJob(39L, null, java.time.LocalDateTime.now().minusMinutes(5));
        JobResultResponseDTO failed = jobResult(39L, 3612L, "FAIL");
        when(jobClient.getResult("provision", 39L)).thenReturn(failed);

        poller.pollProvisionJobs();

        verify(adminRequestCommandService).failApprovalJob(39L, failed);
    }

    private JobResultResponseDTO result(Long requestId, String phase, JobResultResponseDTO.Result made) {
        return new JobResultResponseDTO(String.valueOf(requestId), "provision", 1L, phase, null, null, made);
    }

    @Test
    @DisplayName("작업이 성공하면 만든 자원을 신청에 반영한다")
    void completesOnSuccess() {
        givenProcessing(1L);
        JobResultResponseDTO.Result made = new JobResultResponseDTO.Result(
                50001L, 50001L, "ailab-testuser-abcd", "farm2", List.of());
        when(jobClient.getResult("provision", 1L)).thenReturn(result(1L, "SUCCESS", made));

        poller.pollProvisionJobs();

        verify(adminRequestCommandService).completeApprovalJob(1L, made);
    }

    @Test
    @DisplayName("아직 실행 중이거나 등록 이력이 없으면 아무것도 하지 않는다")
    void ignoresStartAndNone() {
        givenProcessing(1L, 2L);
        when(jobClient.getResult("provision", 1L)).thenReturn(result(1L, "START", null));
        when(jobClient.getResult("provision", 2L)).thenReturn(result(2L, "none", null));

        poller.pollProvisionJobs();

        verify(adminRequestCommandService, never()).completeApprovalJob(anyLong(), any());
        verify(adminRequestCommandService, never()).failApprovalJob(anyLong(), any());
        verify(adminRequestCommandService, never()).reportUnknownApprovalJob(anyLong(), any());
    }

    @Test
    @DisplayName("작업이 실패하면 실패 처리로 넘긴다")
    void failsOnFail() {
        givenProcessing(3L);
        JobResultResponseDTO failed = result(3L, "FAIL", null);
        when(jobClient.getResult("provision", 3L)).thenReturn(failed);

        poller.pollProvisionJobs();

        verify(adminRequestCommandService).failApprovalJob(3L, failed);
    }

    @Test
    @DisplayName("자원을 남긴 실패(DEGRADED)는 되돌리지 않고 신청당 한 번만 관리자 확인으로 알린다")
    void degradedIsReportedOnceWithoutRevert() {
        givenProcessing(7L);
        JobResultResponseDTO degraded = new JobResultResponseDTO("7", "provision", 1L, "FAIL", "DEGRADED", null, null);
        when(jobClient.getResult("provision", 7L)).thenReturn(degraded);

        poller.pollProvisionJobs();
        poller.pollProvisionJobs();

        verify(adminRequestCommandService, never()).failApprovalJob(anyLong(), any());
        verify(adminRequestCommandService, times(1)).reportDegradedApprovalJob(7L, degraded);
    }

    @Test
    @DisplayName("결과 불명은 신청당 한 번만 알린다")
    void reportsUnknownOnce() {
        givenProcessing(4L);
        JobResultResponseDTO unknown = result(4L, "UNKNOWN", null);
        when(jobClient.getResult("provision", 4L)).thenReturn(unknown);

        // 결과 불명은 신청 상태를 그대로 두므로 다음 바퀴에도 같은 신청이 다시 잡힌다.
        poller.pollProvisionJobs();
        poller.pollProvisionJobs();

        verify(adminRequestCommandService, times(1)).reportUnknownApprovalJob(4L, unknown);
    }

    @Test
    @DisplayName("한 신청의 조회가 실패해도 나머지 신청은 계속 처리한다")
    void keepsGoingWhenOneLookupFails() {
        givenProcessing(5L, 6L);
        when(jobClient.getResult("provision", 5L))
                .thenThrow(new BusinessException(ErrorCode.EXTERNAL_API_ERROR));
        JobResultResponseDTO.Result made = new JobResultResponseDTO.Result(
                null, null, "ailab-testuser-efgh", "farm2", List.of());
        when(jobClient.getResult("provision", 6L)).thenReturn(result(6L, "SUCCESS", made));

        poller.pollProvisionJobs();

        verify(adminRequestCommandService).completeApprovalJob(eq(6L), any());
    }
}
