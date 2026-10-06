package DGU_AI_LAB.admin_be.domain.scheduler;

import DGU_AI_LAB.admin_be.domain.home.service.HomeCleanupNotifier;
import DGU_AI_LAB.admin_be.domain.home.service.HomeCleanupService;
import DGU_AI_LAB.admin_be.domain.home.service.HomeRetentionPolicy;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.HomeDeleteRegisterRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.job.JobClient;
import DGU_AI_LAB.admin_be.domain.requests.job.JobRegistrationUnconfirmedException;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("HomeCleanupScheduler")
class HomeCleanupSchedulerTest {

    private static final Long CLEANUP_ID = 3L;
    private static final HomeCleanupService.Target TARGET = new HomeCleanupService.Target(
            CLEANUP_ID, "hong", 55010L, LocalDateTime.of(2026, 8, 1, 0, 0));

    @Mock private UserRepository userRepository;
    @Mock private HomeCleanupService homeCleanupService;
    @Mock private JobClient jobClient;
    @Mock private HomeCleanupNotifier notifier;

    private HomeCleanupScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new HomeCleanupScheduler(
                userRepository, homeCleanupService, new HomeRetentionPolicy(), jobClient, notifier);
    }

    private void candidates(Long... userIds) {
        List<User> users = java.util.Arrays.stream(userIds).map(id -> {
            User user = mock(User.class);
            when(user.getUserId()).thenReturn(id);
            return user;
        }).toList();
        when(userRepository.findHomeCleanupCandidates(any(), any())).thenReturn(users);
    }

    @Test
    @DisplayName("삭제 시도가 만들어지면 작업을 등록하고 작업 번호를 적는다")
    void registersJob() {
        candidates(7L);
        when(homeCleanupService.begin(eq(7L), any())).thenReturn(Optional.of(TARGET));
        when(jobClient.registerHomeDelete(new HomeDeleteRegisterRequestDTO(CLEANUP_ID, "hong", 55010L)))
                .thenReturn(900L);

        scheduler.runHomeCleanup();

        verify(homeCleanupService).recordJob(CLEANUP_ID, 900L);
        verifyNoInteractions(notifier);
    }

    @Test
    @DisplayName("지울 홈이 아니면 작업을 등록하지 않는다")
    void skipsWhenNothingToDelete() {
        candidates(7L);
        when(homeCleanupService.begin(eq(7L), any())).thenReturn(Optional.empty());

        scheduler.runHomeCleanup();

        verifyNoInteractions(jobClient, notifier);
    }

    @Test
    @DisplayName("등록 응답을 받지 못하면 실패로 닫지 않고 결과 확인에 맡긴다")
    void unconfirmedRegistrationIsLeftToPoller() {
        candidates(7L);
        when(homeCleanupService.begin(eq(7L), any())).thenReturn(Optional.of(TARGET));
        when(jobClient.registerHomeDelete(any())).thenThrow(new JobRegistrationUnconfirmedException(
                "timeout", ErrorCode.HOME_DELETE_FAILED, new RuntimeException()));

        scheduler.runHomeCleanup();

        verify(homeCleanupService, never()).fail(anyLong(), anyString());
        verifyNoInteractions(notifier);
    }

    @Test
    @DisplayName("등록이 거절되면 실패로 닫고 관리자에게 알린다")
    void rejectedRegistrationFailsAndNotifies() {
        candidates(7L);
        when(homeCleanupService.begin(eq(7L), any())).thenReturn(Optional.of(TARGET));
        when(jobClient.registerHomeDelete(any())).thenThrow(new RuntimeException("400"));
        when(homeCleanupService.fail(CLEANUP_ID, HomeCleanupScheduler.REGISTRATION_FAILED))
                .thenReturn(Optional.of(TARGET));

        scheduler.runHomeCleanup();

        verify(notifier).failed(TARGET, HomeCleanupScheduler.REGISTRATION_FAILED);
    }

    @Test
    @DisplayName("한 사용자에서 오류가 나도 나머지 사용자는 계속 처리한다")
    void oneFailureDoesNotStopOthers() {
        candidates(7L, 8L);
        when(homeCleanupService.begin(eq(7L), any())).thenThrow(new RuntimeException("db"));
        when(homeCleanupService.begin(eq(8L), any())).thenReturn(Optional.of(TARGET));
        when(jobClient.registerHomeDelete(any())).thenReturn(901L);

        scheduler.runHomeCleanup();

        verify(homeCleanupService).recordJob(CLEANUP_ID, 901L);
    }
}
