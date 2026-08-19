package DGU_AI_LAB.admin_be.domain.scheduler;

import DGU_AI_LAB.admin_be.domain.alarm.service.AlarmService;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import DGU_AI_LAB.admin_be.global.util.MessageUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("UserLifecycleTransactionalService")
class UserLifecycleTransactionalServiceTest {

    @InjectMocks
    private UserLifecycleTransactionalService lifecycleService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AlarmService alarmService;

    @Mock
    private MessageUtils messageUtils;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    @SuppressWarnings("rawtypes")
    private ValueOperations valueOps;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), any(), any(Duration.class))).thenReturn(true);
        when(messageUtils.get(anyString(), any(Object[].class))).thenReturn("mock");
    }

    private User buildUserWithLastLogin(LocalDateTime lastLoginAt) {
        User user = User.builder()
                .email("test@dgu.ac.kr")
                .password("pw")
                .name("홍길동")
                .studentId("2021001234")
                .phone("010-1234-5678")
                .department("컴퓨터공학과")
                .build();
        try {
            var field = User.class.getDeclaredField("lastLoginAt");
            field.setAccessible(true);
            field.set(user, lastLoginAt);
            var idField = User.class.getDeclaredField("userId");
            idField.setAccessible(true);
            idField.set(user, 1L);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return user;
    }

    @Nested
    @DisplayName("processInactiveUser - 경고 알림 중복 방지")
    class WarningDeduplication {

        @Test
        @DisplayName("D-7 경고 대상이면 알림을 발송한다")
        void sendsWarning_whenSevenDaysLeft() {
            LocalDateTime now = LocalDateTime.of(2026, 1, 10, 9, 0);
            LocalDateTime lastLoginAt = now.plusDays(7).minusMonths(3);
            User user = buildUserWithLastLogin(lastLoginAt);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));

            lifecycleService.processInactiveUser(1L, now);

            verify(alarmService, times(1)).sendAllAlerts(any(), any(), any(), any());
        }

        @Test
        @DisplayName("같은 날 동일 유저에게 같은 daysLeft 경고를 두 번 트리거해도 한 번만 발송한다")
        void doesNotResendWarning_onSameDayDuplicateTrigger() {
            LocalDateTime now = LocalDateTime.of(2026, 1, 10, 9, 0);
            LocalDateTime lastLoginAt = now.plusDays(7).minusMonths(3);
            User user = buildUserWithLastLogin(lastLoginAt);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));

            // 첫 호출: SETNX 성공(신규) → 발송
            when(valueOps.setIfAbsent(anyString(), any(), any(Duration.class))).thenReturn(true);
            lifecycleService.processInactiveUser(1L, now);

            // 재실행(재배포/수동 트리거 등): SETNX 실패(이미 존재) → 스킵
            when(valueOps.setIfAbsent(anyString(), any(), any(Duration.class))).thenReturn(false);
            lifecycleService.processInactiveUser(1L, now);

            verify(alarmService, times(1)).sendAllAlerts(any(), any(), any(), any());
        }

        @Test
        @DisplayName("Redis 장애 시에도 경고 발송은 계속 진행한다 (fail-open)")
        void sendsWarning_whenRedisFails() {
            LocalDateTime now = LocalDateTime.of(2026, 1, 10, 9, 0);
            LocalDateTime lastLoginAt = now.plusDays(1).minusMonths(3);
            User user = buildUserWithLastLogin(lastLoginAt);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(valueOps.setIfAbsent(anyString(), any(), any(Duration.class)))
                    .thenThrow(new RuntimeException("Redis down"));

            lifecycleService.processInactiveUser(1L, now);

            verify(alarmService, times(1)).sendAllAlerts(any(), any(), any(), any());
        }

        @Test
        @DisplayName("삭제 예정일이 지난 유저는 중복 방지와 무관하게 Soft Delete된다")
        void softDeletesUser_regardlessOfDedup() {
            LocalDateTime now = LocalDateTime.of(2026, 1, 10, 9, 0);
            LocalDateTime lastLoginAt = now.minusMonths(3).minusDays(1);
            User user = buildUserWithLastLogin(lastLoginAt);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));

            lifecycleService.processInactiveUser(1L, now);

            assertThat(user.getIsActive()).isFalse();
            assertThat(user.getDeletedAt()).isNotNull();
            verify(alarmService, times(1)).sendAllAlerts(any(), any(), any(), any());
        }
    }
}
