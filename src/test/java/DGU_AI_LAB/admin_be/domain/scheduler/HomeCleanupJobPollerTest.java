package DGU_AI_LAB.admin_be.domain.scheduler;

import DGU_AI_LAB.admin_be.domain.home.entity.HomeCleanup;
import DGU_AI_LAB.admin_be.domain.home.entity.HomeCleanupStatus;
import DGU_AI_LAB.admin_be.domain.home.repository.HomeCleanupRepository;
import DGU_AI_LAB.admin_be.domain.home.service.HomeCleanupNotifier;
import DGU_AI_LAB.admin_be.domain.home.service.HomeCleanupService;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.JobResultResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.job.JobClient;
import DGU_AI_LAB.admin_be.domain.requests.job.JobResults;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("HomeCleanupJobPoller")
class HomeCleanupJobPollerTest {

    private static final Long CLEANUP_ID = 3L;
    private static final Long JOB_ID = 900L;
    private static final HomeCleanupService.Target TARGET = new HomeCleanupService.Target(
            CLEANUP_ID, "hong", 55010L, LocalDateTime.of(2026, 8, 1, 0, 0));

    @Mock private HomeCleanupRepository cleanupRepository;
    @Mock private JobClient jobClient;
    @Mock private HomeCleanupService homeCleanupService;
    @Mock private HomeCleanupNotifier notifier;
    @InjectMocks private HomeCleanupJobPoller poller;

    private void processing(Long jobId, LocalDateTime updatedAt) {
        HomeCleanup cleanup = mock(HomeCleanup.class);
        when(cleanup.getHomeCleanupId()).thenReturn(CLEANUP_ID);
        when(cleanup.getJobId()).thenReturn(jobId);
        when(cleanup.getUpdatedAt()).thenReturn(updatedAt);
        when(cleanupRepository.findAllByStatus(HomeCleanupStatus.PROCESSING)).thenReturn(List.of(cleanup));
    }

    private void processing() {
        processing(JOB_ID, LocalDateTime.now().minusMinutes(5));
    }

    private void result(Long jobId, String phase, String errorCode) {
        when(jobClient.getResult(JobResults.KIND_HOME, CLEANUP_ID)).thenReturn(new JobResultResponseDTO(
                String.valueOf(CLEANUP_ID), JobResults.KIND_HOME, jobId, phase, errorCode, null, null));
    }

    @Test
    @DisplayName("작업이 성공하면 삭제 완료로 닫고 알린다")
    void successCompletesAndNotifies() {
        processing();
        result(JOB_ID, JobResults.PHASE_SUCCESS, null);
        when(homeCleanupService.complete(CLEANUP_ID)).thenReturn(Optional.of(TARGET));

        poller.pollHomeCleanups();

        verify(notifier).deleted(TARGET);
        verify(homeCleanupService, never()).fail(anyLong(), anyString());
    }

    @Test
    @DisplayName("이미 닫힌 시도면 알림을 다시 보내지 않는다")
    void alreadySettledDoesNotNotifyAgain() {
        processing();
        result(JOB_ID, JobResults.PHASE_SUCCESS, null);
        when(homeCleanupService.complete(CLEANUP_ID)).thenReturn(Optional.empty());

        poller.pollHomeCleanups();

        verifyNoInteractions(notifier);
    }

    @ParameterizedTest
    @ValueSource(strings = {JobResults.PHASE_START, JobResults.PHASE_RETRY})
    @DisplayName("작업이 도는 중이면 기다린다")
    void runningWaits(String phase) {
        processing();
        result(JOB_ID, phase, null);

        poller.pollHomeCleanups();

        verifyNoInteractions(homeCleanupService, notifier);
    }

    @Test
    @DisplayName("이번에 등록한 작업이 아닌 결과는 반영하지 않는다")
    void otherJobResultIsIgnored() {
        processing();
        result(JOB_ID - 1, JobResults.PHASE_FAIL, "HOME_IN_USE");

        poller.pollHomeCleanups();

        verifyNoInteractions(homeCleanupService, notifier);
    }

    @Test
    @DisplayName("작업이 실패하면 그 오류 코드로 닫고 관리자에게 알린다")
    void failureFailsWithErrorCode() {
        processing();
        result(JOB_ID, JobResults.PHASE_FAIL, "HOME_IN_USE");
        when(homeCleanupService.fail(CLEANUP_ID, "HOME_IN_USE")).thenReturn(Optional.of(TARGET));

        poller.pollHomeCleanups();

        verify(notifier).failed(TARGET, "HOME_IN_USE");
    }

    @ParameterizedTest
    @ValueSource(strings = {JobResults.PHASE_UNKNOWN, JobResults.PHASE_NONE})
    @DisplayName("결과를 알 수 없거나 기록이 없으면 결과 불명으로 닫는다")
    void unknownResultFailsAsUnknown(String phase) {
        processing();
        result(null, phase, null);
        when(homeCleanupService.fail(CLEANUP_ID, HomeCleanupJobPoller.RESULT_UNKNOWN)).thenReturn(Optional.of(TARGET));

        poller.pollHomeCleanups();

        verify(notifier).failed(TARGET, HomeCleanupJobPoller.RESULT_UNKNOWN);
    }

    @Test
    @DisplayName("작업 번호가 없고 등록한 지 얼마 안 됐으면 결과를 조회하지 않는다")
    void awaitingRegistrationIsSkipped() {
        processing(null, LocalDateTime.now());

        poller.pollHomeCleanups();

        verifyNoInteractions(jobClient, homeCleanupService, notifier);
    }

    @Test
    @DisplayName("결과 조회가 실패하면 닫지 않고 다음 바퀴에 다시 본다")
    void lookupFailureLeavesAttemptOpen() {
        processing();
        when(jobClient.getResult(any(), any())).thenThrow(new RuntimeException("timeout"));

        poller.pollHomeCleanups();

        verifyNoInteractions(homeCleanupService, notifier);
    }
}
