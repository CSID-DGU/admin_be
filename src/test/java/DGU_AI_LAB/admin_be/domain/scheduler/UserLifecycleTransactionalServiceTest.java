package DGU_AI_LAB.admin_be.domain.scheduler;

import DGU_AI_LAB.admin_be.domain.alarm.service.AlarmService;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.users.entity.Role;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import DGU_AI_LAB.admin_be.domain.users.service.InactivityNoticeStore;
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
import org.springframework.test.util.ReflectionTestUtils;

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
    private InactivityNoticeStore noticeStore;

    @Mock
    @SuppressWarnings("rawtypes")
    private ValueOperations valueOps;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), any(), any(Duration.class))).thenReturn(true);
        when(messageUtils.get(anyString(), any(Object[].class))).thenReturn("mock");
        // 기본은 기한을 미리 알린 사용자다. 알린 적 없는 경우는 NoticeBeforeDeactivation에서 따로 본다.
        when(noticeStore.findDeadline(anyLong())).thenReturn(Optional.of(NOW.toLocalDate().minusDays(1)));
        when(noticeStore.save(anyLong(), any(), any())).thenReturn(true);
    }

    private static Request request(Status status, LocalDateTime approvedAt, LocalDateTime updatedAt) {
        Request request = mock(Request.class);
        when(request.getStatus()).thenReturn(status);
        when(request.getApprovedAt()).thenReturn(approvedAt);
        when(request.getUpdatedAt()).thenReturn(updatedAt);
        return request;
    }

    /** createdAt = 가입 시각, lastLoginAt = 마지막 로그인(판정에 쓰이지 않음을 확인하는 데만 쓴다). */
    private User buildUser(LocalDateTime createdAt, LocalDateTime lastLoginAt) {
        User user = User.builder()
                .email("test@dgu.ac.kr")
                .password("pw")
                .name("홍길동")
                .studentId("2021001234")
                .phone("010-1234-5678")
                .department("컴퓨터공학과")
                .build();
        ReflectionTestUtils.setField(user, "userId", 1L);
        ReflectionTestUtils.setField(user, "createdAt", createdAt);
        ReflectionTestUtils.setField(user, "lastLoginAt", lastLoginAt);
        return user;
    }

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 1, 10, 9, 0);

    @Nested
    @DisplayName("processInactiveUser - 경고 알림 중복 방지")
    class WarningDeduplication {

        @Test
        @DisplayName("D-7 경고 대상이면 알림을 발송한다")
        void sendsWarning_whenSevenDaysLeft() {
            User user = buildUser(NOW.plusDays(7).minusYears(1), null);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));

            lifecycleService.processInactiveUser(1L, NOW);

            verify(alarmService, times(1)).notifyUser(any(), any(), any(), any());
        }

        @Test
        @DisplayName("같은 날 동일 유저에게 같은 daysLeft 경고를 두 번 트리거해도 한 번만 발송한다")
        void doesNotResendWarning_onSameDayDuplicateTrigger() {
            User user = buildUser(NOW.plusDays(7).minusYears(1), null);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));

            // 첫 호출: SETNX 성공(신규) → 발송
            when(valueOps.setIfAbsent(anyString(), any(), any(Duration.class))).thenReturn(true);
            lifecycleService.processInactiveUser(1L, NOW);

            // 재실행(재배포/수동 트리거 등): SETNX 실패(이미 존재) → 스킵
            when(valueOps.setIfAbsent(anyString(), any(), any(Duration.class))).thenReturn(false);
            lifecycleService.processInactiveUser(1L, NOW);

            verify(alarmService, times(1)).notifyUser(any(), any(), any(), any());
        }

        @Test
        @DisplayName("Redis 장애 시에도 경고 발송은 계속 진행한다 (fail-open)")
        void sendsWarning_whenRedisFails() {
            User user = buildUser(NOW.plusDays(1).minusYears(1), null);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(valueOps.setIfAbsent(anyString(), any(), any(Duration.class)))
                    .thenThrow(new RuntimeException("Redis down"));

            lifecycleService.processInactiveUser(1L, NOW);

            verify(alarmService, times(1)).notifyUser(any(), any(), any(), any());
        }

        @Test
        @DisplayName("삭제 예정일이 지난 유저는 탈퇴 대상으로 돌려주고, 탈퇴 자체는 호출자에게 맡긴다")
        void returnsWithdrawTarget_whenPastDeleteDate() {
            User user = buildUser(NOW.minusYears(1).minusDays(1), null);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));

            boolean withdraw = lifecycleService.processInactiveUser(1L, NOW);

            assertThat(withdraw).isTrue();
            assertThat(user.getIsActive()).isTrue();
            verifyNoInteractions(alarmService);
        }

        @Test
        @DisplayName("경고 대상은 탈퇴 대상이 아니다")
        void warningTargetIsNotWithdrawn() {
            User user = buildUser(NOW.plusDays(3).minusYears(1), null);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));

            assertThat(lifecycleService.processInactiveUser(1L, NOW)).isFalse();
        }
    }

    @Nested
    @DisplayName("processInactiveUser - 경고 없는 비활성화 방지")
    class NoticeBeforeDeactivation {

        private User overdueUser() {
            User user = buildUser(NOW.minusYears(2), null);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            return user;
        }

        @Test
        @DisplayName("기한이 지났는데 경고한 적이 없으면 비활성화하지 않고, 7일 뒤를 예정일로 알리고 기록한다")
        void overdueWithoutNotice_getsGraceWarning() {
            overdueUser();
            when(noticeStore.findDeadline(1L)).thenReturn(Optional.empty());

            assertThat(lifecycleService.processInactiveUser(1L, NOW)).isFalse();

            verify(noticeStore).save(1L, NOW.toLocalDate().plusDays(7), NOW.toLocalDate());
            verify(messageUtils).get("notification.user.delete-warning.body",
                    "홍길동", "7", NOW.toLocalDate().plusDays(7).toString());
            verify(alarmService, times(1)).notifyUser(any(), any(), any(), any());
        }

        @Test
        @DisplayName("유예 예정일을 기록하지 못하면 경고를 보내지 않고 다음 회차로 넘긴다 — 기록 없는 유예는 매일 새로 시작된다")
        void graceNotRecorded_sendsNoWarning() {
            overdueUser();
            when(noticeStore.findDeadline(1L)).thenReturn(Optional.empty());
            when(noticeStore.save(anyLong(), any(), any())).thenReturn(false);

            assertThat(lifecycleService.processInactiveUser(1L, NOW)).isFalse();

            verifyNoInteractions(alarmService);
        }

        @Test
        @DisplayName("기한 전 경고는 예정일을 기록하지 못해도 보낸다")
        void regularWarningNotRecorded_stillSent() {
            User user = buildUser(NOW.plusDays(3).minusYears(1), null);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(noticeStore.save(anyLong(), any(), any())).thenReturn(false);

            lifecycleService.processInactiveUser(1L, NOW);

            verify(alarmService, times(1)).notifyUser(any(), any(), any(), any());
        }

        @Test
        @DisplayName("유예 중에는 알린 예정일 기준으로 3일·1일 전에 다시 경고하고 비활성화하지 않는다")
        void duringGrace_remindsAgainstNoticedDeadline() {
            overdueUser();
            when(noticeStore.findDeadline(1L)).thenReturn(Optional.of(NOW.toLocalDate().plusDays(3)));

            assertThat(lifecycleService.processInactiveUser(1L, NOW)).isFalse();

            verify(alarmService, times(1)).notifyUser(any(), any(), any(), any());
        }

        @Test
        @DisplayName("유예 중 경고일이 아닌 날은 아무것도 하지 않는다")
        void duringGrace_offDay_doesNothing() {
            overdueUser();
            when(noticeStore.findDeadline(1L)).thenReturn(Optional.of(NOW.toLocalDate().plusDays(5)));

            assertThat(lifecycleService.processInactiveUser(1L, NOW)).isFalse();

            verifyNoInteractions(alarmService);
        }

        @Test
        @DisplayName("알린 예정일이 되면 비활성화 대상이다")
        void noticedDeadlineReached_isWithdrawn() {
            overdueUser();
            when(noticeStore.findDeadline(1L)).thenReturn(Optional.of(NOW.toLocalDate()));

            assertThat(lifecycleService.processInactiveUser(1L, NOW)).isTrue();
            verifyNoInteractions(alarmService);
        }

        @Test
        @DisplayName("기한 전 경고(7·3·1일)는 규칙상 기한을 예정일로 기록한다")
        void regularWarning_recordsRuleDate() {
            User user = buildUser(NOW.plusDays(3).minusYears(1), null);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));

            lifecycleService.processInactiveUser(1L, NOW);

            verify(noticeStore).save(1L, NOW.toLocalDate().plusDays(3), NOW.toLocalDate());
            verify(noticeStore, never()).findDeadline(anyLong());
        }

        @Test
        @DisplayName("경고 기록을 읽지 못하면 비활성화하지 않고 예외로 그 회차를 건너뛴다")
        void storeFailure_doesNotWithdraw() {
            overdueUser();
            when(noticeStore.findDeadline(1L)).thenThrow(new IllegalStateException("Redis down"));

            org.assertj.core.api.Assertions.assertThatThrownBy(() -> lifecycleService.processInactiveUser(1L, NOW))
                    .isInstanceOf(IllegalStateException.class);
            verifyNoInteractions(alarmService);
        }
    }

    @Nested
    @DisplayName("processInactiveUser - 미사용 기준 시각")
    class InactiveSince {

        @Test
        @DisplayName("마지막 컨테이너가 끝난 지 1년이 지나면 대상이다")
        void containerEndedOverOneYearAgo_isWithdrawn() {
            User user = buildUser(NOW.minusYears(3), null);
            user.getRequests().add(request(Status.DELETED, NOW.minusYears(2), NOW.minusYears(1).minusDays(1)));
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));

            assertThat(lifecycleService.processInactiveUser(1L, NOW)).isTrue();
        }

        @Test
        @DisplayName("마지막 컨테이너가 끝난 지 1년이 안 됐으면 가입이 오래됐어도 대상이 아니다")
        void containerEndedWithinOneYear_isKept() {
            User user = buildUser(NOW.minusYears(3), null);
            user.getRequests().add(request(Status.DELETED, NOW.minusYears(2), NOW.minusYears(2)));
            user.getRequests().add(request(Status.DELETED, NOW.minusYears(1), NOW.minusMonths(11)));
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));

            assertThat(lifecycleService.processInactiveUser(1L, NOW)).isFalse();
        }

        @Test
        @DisplayName("로그인은 보지 않는다 — 최근에 로그인했어도 컨테이너가 끝난 지 1년이 지나면 대상이다")
        void recentLogin_doesNotKeepUser() {
            User user = buildUser(NOW.minusYears(3), NOW.minusDays(1));
            user.getRequests().add(request(Status.DELETED, NOW.minusYears(2), NOW.minusYears(1).minusDays(1)));
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));

            assertThat(lifecycleService.processInactiveUser(1L, NOW)).isTrue();
        }

        @Test
        @DisplayName("컨테이너를 쓴 적이 없으면 가입 시각부터 1년을 센다")
        void neverHadContainer_countsFromSignup() {
            User oldSignup = buildUser(NOW.minusYears(1).minusDays(1), NOW.minusDays(1));
            when(userRepository.findById(1L)).thenReturn(Optional.of(oldSignup));
            assertThat(lifecycleService.processInactiveUser(1L, NOW)).isTrue();

            User recentSignup = buildUser(NOW.minusMonths(11), null);
            when(userRepository.findById(1L)).thenReturn(Optional.of(recentSignup));
            assertThat(lifecycleService.processInactiveUser(1L, NOW)).isFalse();
        }

        @Test
        @DisplayName("기준 시각이 전혀 없으면 판정을 건너뛴다")
        void skips_whenNoBaseline() {
            User user = buildUser(null, null);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));

            assertThat(lifecycleService.processInactiveUser(1L, NOW)).isFalse();
            verifyNoInteractions(alarmService);
        }

        @Test
        @DisplayName("끝나지 않은 신청(컨테이너 포함)이 있으면 대상이 아니다")
        void userWithOpenRequest_isKept() {
            User user = buildUser(NOW.minusYears(3), null);
            user.getRequests().add(request(Status.FULFILLED, NOW.minusYears(2), NOW.minusYears(2)));
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));

            assertThat(lifecycleService.processInactiveUser(1L, NOW)).isFalse();
            verifyNoInteractions(alarmService);
        }

        @Test
        @DisplayName("승인 전에 취소된 신청은 컨테이너가 없었으니 기준 시각에 넣지 않는다")
        void cancelledBeforeApproval_isIgnored() {
            User user = buildUser(NOW.minusYears(1).minusDays(1), null);
            user.getRequests().add(request(Status.DELETED, null, NOW.minusDays(1)));
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));

            assertThat(lifecycleService.processInactiveUser(1L, NOW)).isTrue();
        }

        @Test
        @DisplayName("관리자는 경고·탈퇴 대상이 아니다")
        void admin_isNeverWithdrawn() {
            User user = buildUser(NOW.minusYears(3), null);
            user.changeRole(Role.ADMIN);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));

            assertThat(lifecycleService.processInactiveUser(1L, NOW)).isFalse();
            verifyNoInteractions(alarmService);
        }

        @Test
        @DisplayName("이미 비활성화된 유저는 탈퇴 대상이 아니다")
        void inactiveUser_isSkipped() {
            User user = buildUser(NOW.minusYears(3), null);
            user.deactivate();
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));

            assertThat(lifecycleService.processInactiveUser(1L, NOW)).isFalse();
        }
    }
}
