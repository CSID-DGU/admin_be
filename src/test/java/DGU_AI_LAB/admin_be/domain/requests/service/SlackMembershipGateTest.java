package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.domain.alarm.service.SlackApiService;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("SlackMembershipGate")
class SlackMembershipGateTest {

    private final SlackApiService slack = mock(SlackApiService.class);
    private final Clock clock = mock(Clock.class);

    private SlackMembershipGate gate(boolean enabled) {
        when(clock.millis()).thenReturn(Instant.parse("2026-10-09T00:00:00Z").toEpochMilli());
        return new SlackMembershipGate(slack, enabled, clock);
    }

    private static void assertRejected(Runnable call) {
        assertThatThrownBy(call::run).isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.SLACK_MEMBERSHIP_REQUIRED);
    }

    @Test
    @DisplayName("꺼져 있으면 Slack을 조회하지 않고 통과시킨다")
    void disabledSkipsLookup() {
        gate(false).requireMember("홍길동");

        verifyNoInteractions(slack);
    }

    @Test
    @DisplayName("같은 이름의 회원이 있으면 통과시키고 목록을 다시 받지 않는다")
    void memberPasses() {
        when(slack.hasMemberNamed("홍길동")).thenReturn(true);

        gate(true).requireMember("홍길동");

        verify(slack, never()).refreshSlackUserCache();
    }

    @Test
    @DisplayName("없으면 목록을 다시 받아 한 번 더 보고, 방금 가입한 사람은 통과시킨다")
    void refreshesOnceBeforeRejecting() {
        when(slack.hasMemberNamed("홍길동")).thenReturn(false, true);

        gate(true).requireMember("홍길동");

        verify(slack).refreshSlackUserCache();
    }

    @Test
    @DisplayName("다시 받아도 없으면 거절하고, 간격 안에는 목록을 또 받지 않는다")
    void rejectsAndLimitsRefresh() {
        when(slack.hasMemberNamed("홍길동")).thenReturn(false);
        SlackMembershipGate gate = gate(true);

        assertRejected(() -> gate.requireMember("홍길동"));
        assertRejected(() -> gate.requireMember("홍길동"));
        verify(slack, times(1)).refreshSlackUserCache();

        when(clock.millis()).thenReturn(Instant.parse("2026-10-09T00:01:00Z").toEpochMilli());
        assertRejected(() -> gate.requireMember("홍길동"));
        verify(slack, times(2)).refreshSlackUserCache();
    }

    @Test
    @DisplayName("Slack을 조회하지 못하면 신청을 막지 않는다")
    void lookupFailurePasses() {
        when(slack.hasMemberNamed("홍길동")).thenThrow(new BusinessException(ErrorCode.SLACK_USER_NOT_FOUND));

        assertThatCode(() -> gate(true).requireMember("홍길동")).doesNotThrowAnyException();
    }
}
