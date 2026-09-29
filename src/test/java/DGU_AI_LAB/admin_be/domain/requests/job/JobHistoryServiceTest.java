package DGU_AI_LAB.admin_be.domain.requests.job;

import DGU_AI_LAB.admin_be.domain.requests.dto.response.JobHistoryResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.JobStepsResponseDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("JobHistoryService")
class JobHistoryServiceTest {

    // 신청 생성 2026-09-15 10:20:41 (서울) = 01:20:41Z
    private static final LocalDateTime REQUEST_CREATED_AT = LocalDateTime.of(2026, 9, 15, 10, 20, 41);

    @Mock private JobClient jobClient;
    @InjectMocks private JobHistoryService service;

    private static JobStepsResponseDTO.Job jobStartedAt(long jobId, String startedAt) {
        return new JobStepsResponseDTO.Job(jobId, startedAt, startedAt, "SUCCESS", null, List.of());
    }

    private void givenSteps(long requestId, List<JobStepsResponseDTO.Job> provision, List<JobStepsResponseDTO.Job> revoke,
                            List<JobStepsResponseDTO.Job> migrate) {
        String id = String.valueOf(requestId);
        when(jobClient.getSteps(JobResults.KIND_PROVISION, requestId)).thenReturn(new JobStepsResponseDTO(id, "provision", provision));
        when(jobClient.getSteps(JobResults.KIND_REVOKE, requestId)).thenReturn(new JobStepsResponseDTO(id, "revoke", revoke));
        when(jobClient.getSteps(JobResults.KIND_MIGRATE, requestId)).thenReturn(new JobStepsResponseDTO(id, "migrate", migrate));
    }

    @Test
    @DisplayName("생성·회수·마이그레이션 단계 기록을 각각 조회해 함께 돌려준다")
    void getsJobHistory() {
        givenSteps(41L, List.of(jobStartedAt(183, "2026-09-15T04:12:06Z")), List.of(), List.of(jobStartedAt(190, "2026-09-15T05:00:00Z")));

        JobHistoryResponseDTO history = service.getJobHistory(41L, REQUEST_CREATED_AT);

        assertThat(history.provision().jobs()).extracting(JobStepsResponseDTO.Job::jobId).containsExactly(183L);
        assertThat(history.revoke().jobs()).isEmpty();
        assertThat(history.migrate().jobs()).extracting(JobStepsResponseDTO.Job::jobId).containsExactly(190L);
    }

    @Test
    @DisplayName("신청이 만들어지기 전에 시작된 작업은 같은 번호를 쓰던 옛 신청의 것이라 뺀다")
    void dropsJobsStartedBeforeRequestCreated() {
        givenSteps(2L, List.of(jobStartedAt(275, "2026-09-15T04:24:23Z"), jobStartedAt(103, "2026-09-14T23:58:24Z")),
                List.of(jobStartedAt(123, "2026-09-14T23:59:58Z")), List.of());

        JobHistoryResponseDTO history = service.getJobHistory(2L, REQUEST_CREATED_AT);

        assertThat(history.provision().jobs()).extracting(JobStepsResponseDTO.Job::jobId).containsExactly(275L);
        assertThat(history.revoke().jobs()).isEmpty();
    }

    @Test
    @DisplayName("신청 직후 시작된 작업은 시계 차이가 조금 있어도 남긴다")
    void keepsJobsWithinClockSkew() {
        givenSteps(2L, List.of(jobStartedAt(7, "2026-09-15T01:20:11Z")), List.of(), List.of());

        assertThat(service.getJobHistory(2L, REQUEST_CREATED_AT).provision().jobs()).hasSize(1);
    }

    @Test
    @DisplayName("시작 시각을 읽을 수 없는 작업은 거르지 않는다")
    void keepsJobsWithUnreadableStart() {
        givenSteps(2L, List.of(jobStartedAt(7, null), jobStartedAt(8, "not-a-time")), List.of(), List.of());

        assertThat(service.getJobHistory(2L, REQUEST_CREATED_AT).provision().jobs()).hasSize(2);
    }
}
