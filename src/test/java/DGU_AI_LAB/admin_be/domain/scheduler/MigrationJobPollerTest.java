package DGU_AI_LAB.admin_be.domain.scheduler;

import DGU_AI_LAB.admin_be.domain.requests.dto.response.JobResultResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.requests.service.OperationJobService;
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
    @Mock private OperationJobService operationJobService;
    @Mock private PodMigrationService podMigrationService;

    private MigrationJobPoller poller;

    @BeforeEach
    void setUp() {
        poller = new MigrationJobPoller(requestRepository, operationJobService, podMigrationService);
        Request request = mock(Request.class);
        when(request.getRequestId()).thenReturn(1L);
        when(requestRepository.findAllByStatus(Status.MIGRATING)).thenReturn(List.of(request));
    }

    private void given(String phase, String errorCode, JobResultResponseDTO.Result made) {
        when(operationJobService.getResult("migrate", 1L))
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
    @DisplayName("실행 중이면 아무것도 하지 않는다")
    void running() {
        given("START", null, null);
        poller.pollMigrationJobs();
        verifyNoInteractions(podMigrationService);
    }
}
