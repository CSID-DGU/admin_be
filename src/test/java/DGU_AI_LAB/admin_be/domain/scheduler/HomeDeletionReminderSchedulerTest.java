package DGU_AI_LAB.admin_be.domain.scheduler;

import DGU_AI_LAB.admin_be.domain.alarm.service.AlarmService;
import DGU_AI_LAB.admin_be.domain.home.service.HomeRetentionPolicy;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import DGU_AI_LAB.admin_be.global.util.MessageUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("HomeDeletionReminderScheduler")
class HomeDeletionReminderSchedulerTest {

    // 안내 삭제일은 마지막 컨테이너가 끝난 날 + 30일이다(HomeRetentionPolicy 기본값).
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);

    @Mock private UserRepository userRepository;
    @Mock private AlarmService alarmService;
    @Mock private MessageUtils messageUtils;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOps;

    private HomeDeletionReminderScheduler scheduler;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), any(), any(Duration.class))).thenReturn(true);
        when(messageUtils.get(anyString(), any(Object[].class))).thenAnswer(i -> i.getArgument(0));
        when(userRepository.findHomeReminderCandidates(any(), any(), any())).thenReturn(List.of());
        scheduler = new HomeDeletionReminderScheduler(
                userRepository, new HomeRetentionPolicy(), alarmService, messageUtils, redisTemplate);
    }

    private User user(Long id, String name, String email, String username) {
        User user = mock(User.class);
        when(user.getUserId()).thenReturn(id);
        when(user.getName()).thenReturn(name);
        when(user.getEmail()).thenReturn(email);
        when(user.getUbuntuUsername()).thenReturn(username);
        return user;
    }

    @Test
    @DisplayName("안내한 삭제일 7일 전과 1일 전에 끝난 날짜를 기준으로 후보를 찾는다")
    void queriesEndDatesForSevenAndOneDaysBefore() {
        scheduler.remind(TODAY);

        // 7일 전: 11/17이 삭제일이면 컨테이너는 10/18에 끝났다. 1일 전: 11/11이 삭제일이면 10/12.
        verify(userRepository).findHomeReminderCandidates(
                eq(LocalDateTime.of(2026, 10, 18, 0, 0)), eq(LocalDateTime.of(2026, 10, 19, 0, 0)), any());
        verify(userRepository).findHomeReminderCandidates(
                eq(LocalDateTime.of(2026, 10, 12, 0, 0)), eq(LocalDateTime.of(2026, 10, 13, 0, 0)), any());
    }

    @Test
    @DisplayName("후보에게 남은 일수와 삭제일을 넣어 메일·DM으로 안내한다")
    void notifiesCandidate() {
        User hong = user(7L, "홍길동", "hong@dgu.ac.kr", "hong");
        when(userRepository.findHomeReminderCandidates(
                eq(LocalDateTime.of(2026, 10, 18, 0, 0)), any(), any())).thenReturn(List.of(hong));

        scheduler.remind(TODAY);

        verify(messageUtils).get("notification.user.home-delete-reminder.subject", "7");
        verify(messageUtils).get("notification.user.home-delete-reminder.body",
                "홍길동", "hong", "7", "2026-11-17");
        verify(alarmService).notifyUser(eq("홍길동"), eq("hong@dgu.ac.kr"),
                eq("notification.user.home-delete-reminder.subject"),
                eq("notification.user.home-delete-reminder.body"));
    }

    @Test
    @DisplayName("같은 날 이미 보낸 안내는 다시 보내지 않는다")
    void skipsAlreadySent() {
        User hong = user(7L, "홍길동", "hong@dgu.ac.kr", "hong");
        when(userRepository.findHomeReminderCandidates(
                eq(LocalDateTime.of(2026, 10, 18, 0, 0)), any(), any())).thenReturn(List.of(hong));
        when(valueOps.setIfAbsent(anyString(), any(), any(Duration.class))).thenReturn(false);

        scheduler.remind(TODAY);

        verify(alarmService, never()).notifyUser(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("한 사용자의 발송이 실패해도 나머지에게는 보낸다")
    void oneFailureDoesNotBlockOthers() {
        User first = user(1L, "가", "a@dgu.ac.kr", "a");
        User second = user(2L, "나", "b@dgu.ac.kr", "b");
        when(userRepository.findHomeReminderCandidates(
                eq(LocalDateTime.of(2026, 10, 18, 0, 0)), any(), any())).thenReturn(List.of(first, second));
        doThrow(new RuntimeException("smtp down")).when(alarmService)
                .notifyUser(eq("가"), anyString(), anyString(), anyString());

        scheduler.remind(TODAY);

        verify(alarmService).notifyUser(eq("나"), eq("b@dgu.ac.kr"), anyString(), anyString());
    }

    @Test
    @DisplayName("Redis에 닿지 못하면 중복 확인을 건너뛰고 보낸다")
    void sendsWhenRedisIsDown() {
        User hong = user(7L, "홍길동", "hong@dgu.ac.kr", "hong");
        when(userRepository.findHomeReminderCandidates(
                eq(LocalDateTime.of(2026, 10, 18, 0, 0)), any(), any())).thenReturn(List.of(hong));
        when(valueOps.setIfAbsent(anyString(), any(), any(Duration.class))).thenThrow(new RuntimeException("redis down"));

        scheduler.remind(TODAY);

        verify(alarmService).notifyUser(eq("홍길동"), anyString(), anyString(), anyString());
    }
}
