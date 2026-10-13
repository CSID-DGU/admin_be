package DGU_AI_LAB.admin_be.domain.scheduler;

import DGU_AI_LAB.admin_be.global.alert.InMemoryAlertDeduplicator;
import DGU_AI_LAB.admin_be.domain.requests.job.JobClient;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.JobResultResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.requests.service.PodMigrationService;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("MigrationJobPoller")
class MigrationJobPollerTest {

    @Mock private RequestRepository requestRepository;
    @Mock private JobClient jobClient;
    @Mock private PodMigrationService podMigrationService;

    private MigrationJobPoller poller;

    @BeforeEach
    void setUp() {
        poller = new MigrationJobPoller(requestRepository, jobClient, new InMemoryAlertDeduplicator(), podMigrationService);
        Request request = mock(Request.class);
        when(request.getRequestId()).thenReturn(1L);
        when(request.getJobId()).thenReturn(5L); // given()의 작업 번호와 같다
        when(requestRepository.findAllByStatus(Status.MIGRATING)).thenReturn(List.of(request));
    }

    private void given(String phase, String errorCode, JobResultResponseDTO.Result made) {
        when(jobClient.getResult("migrate", 1L))
                .thenReturn(new JobResultResponseDTO("1", "migrate", 5L, phase, errorCode, null, made));
    }

    @Test
    @DisplayName("성공이면 결과를 반영한다")
    void success() {
        JobResultResponseDTO.Result made = new JobResultResponseDTO.Result(null, null, "p", "farm7", List.of());
        given("SUCCESS", null, made);
        poller.pollMigrationJobs();
        verify(podMigrationService).completeMigrationJob(1L, made);
    }

    @Test
    @DisplayName("실패면 되돌린다")
    void fail() {
        given("FAIL", "POD_NOT_FOUND", null);
        poller.pollMigrationJobs();
        verify(podMigrationService).failMigrationJob(any(), any());
    }

    @Test
    @DisplayName("자원을 남긴 실패와 결과 불명은 되돌리지 않고 한 번만 알린다")
    void degradedAndUnknownReportOnce() {
        given("FAIL", "DEGRADED", null);
        poller.pollMigrationJobs();
        poller.pollMigrationJobs();
        verify(podMigrationService, times(1)).reportUnresolvedMigrationJob(any(), any());
        verify(podMigrationService, never()).failMigrationJob(any(), any());
    }

    @Test
    @DisplayName("미해결로 확인된 작업은 다시 볼 시각까지 실행기에 묻지 않고, 그 뒤에는 다시 물어 바뀐 결과를 반영한다")
    void unresolvedIsRecheckedOnlyAfterInterval() {
        java.time.Instant start = java.time.Instant.parse("2026-10-13T00:00:00Z");
        poller.clock = java.time.Clock.fixed(start, java.time.ZoneOffset.UTC);
        given("UNKNOWN", null, null);
        poller.pollMigrationJobs();
        poller.pollMigrationJobs();
        verify(jobClient, times(1)).getResult("migrate", 1L);

        JobResultResponseDTO.Result made = new JobResultResponseDTO.Result(null, null, "p", "farm7", List.of());
        given("SUCCESS", null, made);
        poller.clock = java.time.Clock.fixed(start.plus(JobResultPoller.UNRESOLVED_RECHECK_INTERVAL), java.time.ZoneOffset.UTC);
        poller.pollMigrationJobs();
        verify(jobClient, times(2)).getResult("migrate", 1L);
        verify(podMigrationService).completeMigrationJob(1L, made);
    }

    @Test
    @DisplayName("미해결이던 신청에 새 작업이 등록되면 기다리지 않고 바로 묻는다")
    void newJobIsPolledImmediately() {
        given("FAIL", "DEGRADED", null);
        poller.pollMigrationJobs();

        Request retried = mock(Request.class);
        when(retried.getRequestId()).thenReturn(1L);
        when(retried.getJobId()).thenReturn(6L);
        when(requestRepository.findAllByStatus(Status.MIGRATING)).thenReturn(List.of(retried));
        when(jobClient.getResult("migrate", 1L))
                .thenReturn(new JobResultResponseDTO("1", "migrate", 6L, "FAIL", "POD_NOT_FOUND", null, null));
        poller.pollMigrationJobs();

        verify(jobClient, times(2)).getResult("migrate", 1L);
        verify(podMigrationService).failMigrationJob(any(), any());
    }

    @Test
    @DisplayName("실행 중이면 아무것도 하지 않는다")
    void running() {
        given("START", null, null);
        poller.pollMigrationJobs();
        verifyNoInteractions(podMigrationService);
    }

    @Test
    @DisplayName("결과 반영이 예외로 실패해도(기본 구현은 알리지 않음) 폴러는 죽지 않고 조용히 넘어간다 — 회귀 확인")
    void applyFailureIsSilentByDefault() {
        JobResultResponseDTO.Result made = new JobResultResponseDTO.Result(null, null, "p", "farm7", List.of());
        given("SUCCESS", null, made);
        doThrow(new RuntimeException("db busy")).when(podMigrationService).completeMigrationJob(1L, made);

        poller.pollMigrationJobs(); // 예외가 밖으로 나가면 이 시험 자체가 실패한다

        verify(podMigrationService).completeMigrationJob(1L, made);
        verifyNoMoreInteractions(podMigrationService);
    }

    @Test
    @DisplayName("재마이그레이션 직후 보이는 이전 작업의 결과는 반영하지 않는다")
    void ignoresPreviousJobResult() {
        Request request = mock(Request.class);
        when(request.getRequestId()).thenReturn(1L);
        when(request.getJobId()).thenReturn(6L);
        when(requestRepository.findAllByStatus(Status.MIGRATING)).thenReturn(List.of(request));
        given("SUCCESS", null, null);

        poller.pollMigrationJobs();

        verifyNoInteractions(podMigrationService);
    }
}
