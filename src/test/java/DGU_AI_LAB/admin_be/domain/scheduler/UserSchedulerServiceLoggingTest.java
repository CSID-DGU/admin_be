package DGU_AI_LAB.admin_be.domain.scheduler;

import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import DGU_AI_LAB.admin_be.support.LogCaptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/**
 * 수명주기 처리 실패 로그가 사용자 이메일 대신 userId로 대상을 식별하는지 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class UserSchedulerServiceLoggingTest {

    private static final String EMAIL = "inactive@dgu.ac.kr";

    @InjectMocks
    private UserSchedulerService userSchedulerService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserLifecycleTransactionalService userLifecycleService;

    @Test
    @DisplayName("수명주기 처리 실패 로그에 이메일 대신 userId가 남는다")
    void logsUserIdInsteadOfEmailOnFailure() {
        User user = User.builder()
                .email(EMAIL)
                .password("encoded")
                .name("비활성유저")
                .studentId("2019001234")
                .phone("010-0000-0000")
                .department("컴퓨터공학과")
                .build();
        ReflectionTestUtils.setField(user, "userId", 42L);

        when(userRepository.findInactiveUsers(any(LocalDateTime.class))).thenReturn(List.of(user));
        doThrow(new IllegalStateException("처리 실패"))
                .when(userLifecycleService).processInactiveUser(anyLong(), any(LocalDateTime.class));

        try (LogCaptor logCaptor = LogCaptor.forClass(UserSchedulerService.class)) {
            userSchedulerService.runUserLifecycleScheduler();

            String logs = logCaptor.joined();
            assertThat(logs).doesNotContain(EMAIL);
            assertThat(logs).contains("유저(42)");
        }
    }
}
