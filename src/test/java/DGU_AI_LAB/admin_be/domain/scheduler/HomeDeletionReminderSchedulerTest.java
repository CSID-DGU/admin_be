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
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("HomeDeletionReminderScheduler")
class HomeDeletionReminderSchedulerTest {

    // 안내 삭제일은 마지막 컨테이너가 끝난 날 + 30일이다(HomeRetentionPolicy 기본값).
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);
    // 10/18에 끝난 계정의 안내 삭제일은 11/17이다.
    private static final LocalDate ENDED_10_18 = LocalDate.of(2026, 10, 18);

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
        when(alarmService.notifyUser(anyString(), anyString(), anyString(), anyString())).thenReturn(true);
        when(userRepository.findHomeReminderCandidates(any(), any(), any())).thenReturn(List.of());
        scheduler = new HomeDeletionReminderScheduler(
                userRepository, new HomeRetentionPolicy(), alarmService, messageUtils, redisTemplate);
    }

    private User user(Long id, String name, String email, String username, LocalDate endedOn) {
        User user = mock(User.class);
        when(user.getUserId()).thenReturn(id);
        when(user.getName()).thenReturn(name);
        when(user.getEmail()).thenReturn(email);
        when(user.getUbuntuUsername()).thenReturn(username);
        when(user.lastContainerEndedAt()).thenReturn(Optional.of(endedOn.atStartOfDay()));
        return user;
    }

    private void candidates(User... users) {
        when(userRepository.findHomeReminderCandidates(any(), any(), any())).thenReturn(List.of(users));
    }

    @Test
    @DisplayName("삭제일이 오늘 뒤 7일 안에 든 계정을 찾는 창으로 후보를 찾는다")
    void queriesCatchUpWindow() {
        scheduler.remind(TODAY);

        // 삭제일 11/11~11/17 = 끝난 날 10/12~10/18. 끝난 시각 [10/12 0시, 10/19 0시).
        verify(userRepository).findHomeReminderCandidates(
                eq(LocalDateTime.of(2026, 10, 12, 0, 0)), eq(LocalDateTime.of(2026, 10, 19, 0, 0)), any());
    }

    @Test
    @DisplayName("후보에게 남은 일수와 삭제일을 넣어 메일·DM으로 안내한다")
    void notifiesCandidate() {
        candidates(user(7L, "홍길동", "hong@dgu.ac.kr", "hong", ENDED_10_18));

        scheduler.remind(TODAY);

        verify(messageUtils).get("notification.user.home-delete-reminder.subject", "7");
        verify(messageUtils).get("notification.user.home-delete-reminder.body",
                "홍길동", "hong", "7", "2026-11-17");
        verify(alarmService).notifyUser(eq("홍길동"), eq("hong@dgu.ac.kr"),
                eq("notification.user.home-delete-reminder.subject"),
                eq("notification.user.home-delete-reminder.body"));
    }

    @Test
    @DisplayName("같은 주기·같은 단계의 안내가 이미 잡혀 있으면 다시 보내지 않는다")
    void skipsAlreadyClaimed() {
        candidates(user(7L, "홍길동", "hong@dgu.ac.kr", "hong", ENDED_10_18));
        when(valueOps.setIfAbsent(anyString(), any(), any(Duration.class))).thenReturn(false);

        scheduler.remind(TODAY);

        verify(alarmService, never()).notifyUser(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("발송이 실패하면 키를 풀어, 다음 날 같은 단계로 다시 보낸다")
    void releasesClaimWhenSendFailsAndRetriesNextDay() {
        candidates(user(7L, "홍길동", "hong@dgu.ac.kr", "hong", ENDED_10_18));
        when(alarmService.notifyUser(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(false, true);

        scheduler.remind(TODAY);
        verify(redisTemplate, times(1)).delete(anyString());

        scheduler.remind(TODAY.plusDays(1));

        verify(alarmService, times(2)).notifyUser(eq("홍길동"), eq("hong@dgu.ac.kr"), anyString(), anyString());
    }

    @Test
    @DisplayName("발송이 성공했으면 키를 유지해 같은 단계는 다음 날 다시 보내지 않는다")
    void sendsOnlyOnceAfterSuccess() {
        candidates(user(7L, "홍길동", "hong@dgu.ac.kr", "hong", ENDED_10_18));
        when(valueOps.setIfAbsent(anyString(), any(), any(Duration.class))).thenReturn(true, false);

        scheduler.remind(TODAY);
        scheduler.remind(TODAY.plusDays(1));

        verify(alarmService, times(1)).notifyUser(anyString(), anyString(), anyString(), anyString());
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    @DisplayName("남은 날이 1일이면 D-1 안내 한 통만 보낸다")
    void oneDayLeftSendsSingleNotice() {
        candidates(user(7L, "홍길동", "hong@dgu.ac.kr", "hong", LocalDate.of(2026, 10, 12)));

        scheduler.remind(TODAY);

        verify(messageUtils).get("notification.user.home-delete-reminder.subject", "1");
        verify(alarmService, times(1)).notifyUser(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("삭제일 당일이거나 지난 계정에게는 보내지 않는다")
    void neverSendsOnOrAfterDeletionDate() {
        candidates(
                user(1L, "가", "a@dgu.ac.kr", "a", LocalDate.of(2026, 10, 11)),  // 삭제일 = 오늘
                user(2L, "나", "b@dgu.ac.kr", "b", LocalDate.of(2026, 10, 1)));  // 삭제일 = 10/31

        scheduler.remind(TODAY);

        verify(alarmService, never()).notifyUser(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("한 사용자의 발송이 실패해도 나머지에게는 보낸다")
    void oneFailureDoesNotBlockOthers() {
        candidates(
                user(1L, "가", "a@dgu.ac.kr", "a", ENDED_10_18),
                user(2L, "나", "b@dgu.ac.kr", "b", ENDED_10_18));
        doThrow(new RuntimeException("smtp down")).when(alarmService)
                .notifyUser(eq("가"), anyString(), anyString(), anyString());

        scheduler.remind(TODAY);

        verify(alarmService).notifyUser(eq("나"), eq("b@dgu.ac.kr"), anyString(), anyString());
        verify(redisTemplate, times(1)).delete(anyString());
    }

    @Test
    @DisplayName("Redis에 닿지 못하면 중복 확인을 건너뛰고 보낸다")
    void sendsWhenRedisIsDown() {
        candidates(user(7L, "홍길동", "hong@dgu.ac.kr", "hong", ENDED_10_18));
        when(valueOps.setIfAbsent(anyString(), any(), any(Duration.class))).thenThrow(new RuntimeException("redis down"));

        scheduler.remind(TODAY);

        verify(alarmService).notifyUser(eq("홍길동"), anyString(), anyString(), anyString());
    }
}
