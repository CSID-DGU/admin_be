package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.domain.alarm.service.SlackApiService;
import DGU_AI_LAB.admin_be.domain.alarm.service.SlackApiService.Membership;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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

    private static final List<String> EMAILS = List.of("hong@dgu.ac.kr", "hong@gmail.com");

    private final SlackApiService slack = mock(SlackApiService.class);
    private final SlackMembershipRefusalNotice refusalNotice = mock(SlackMembershipRefusalNotice.class);
    private final Clock clock = mock(Clock.class);
    private final User user = User.builder().name("홍길동").email("hong@dgu.ac.kr").contactEmail("hong@gmail.com").build();

    private SlackMembershipGate gate(boolean enabled) {
        when(clock.millis()).thenReturn(Instant.parse("2026-10-09T00:00:00Z").toEpochMilli());
        return new SlackMembershipGate(slack, refusalNotice, enabled, clock);
    }

    private static void assertRejected(Runnable call) {
        assertThatThrownBy(call::run).isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.SLACK_MEMBERSHIP_REQUIRED);
    }

    @Test
    @DisplayName("꺼져 있으면 Slack을 조회하지 않고 통과시킨다")
    void disabledSkipsLookup() {
        gate(false).requireMember(user);

        verifyNoInteractions(slack, refusalNotice);
    }

    @Test
    @DisplayName("이름과 이메일(학교 이메일 또는 자주 사용하는 이메일)이 같은 회원이 있으면 통과시키고 목록을 다시 받지 않는다")
    void memberPasses() {
        when(slack.findMembership("홍길동", EMAILS)).thenReturn(Membership.CONFIRMED);

        gate(true).requireMember(user);

        verify(slack, never()).refreshSlackUserCache();
        verifyNoInteractions(refusalNotice);
    }

    @Test
    @DisplayName("Slack이 이메일을 주지 않으면 이름만 보고 통과시킨다")
    void nameOnlyPasses() {
        when(slack.findMembership("홍길동", EMAILS)).thenReturn(Membership.NAME_ONLY);

        assertThatCode(() -> gate(true).requireMember(user)).doesNotThrowAnyException();
        verifyNoInteractions(refusalNotice);
    }

    @Test
    @DisplayName("없으면 목록을 다시 받아 한 번 더 보고, 방금 가입한 사람은 통과시킨다")
    void refreshesOnceBeforeRejecting() {
        when(slack.findMembership("홍길동", EMAILS)).thenReturn(Membership.NOT_FOUND, Membership.CONFIRMED);

        gate(true).requireMember(user);

        verify(slack).refreshSlackUserCache();
        verifyNoInteractions(refusalNotice);
    }

    @Test
    @DisplayName("다시 받아도 없으면 거절하면서 안내 메일을 맡기고, 간격 안에는 목록을 또 받지 않는다")
    void rejectsAndLimitsRefresh() {
        when(slack.findMembership("홍길동", EMAILS)).thenReturn(Membership.NOT_FOUND);
        SlackMembershipGate gate = gate(true);

        assertRejected(() -> gate.requireMember(user));
        assertRejected(() -> gate.requireMember(user));
        verify(slack, times(1)).refreshSlackUserCache();
        verify(refusalNotice, times(2)).send(user);

        when(clock.millis()).thenReturn(Instant.parse("2026-10-09T00:01:00Z").toEpochMilli());
        assertRejected(() -> gate.requireMember(user));
        verify(slack, times(2)).refreshSlackUserCache();
    }

    @Test
    @DisplayName("Slack을 조회하지 못하면 신청을 막지 않는다")
    void lookupFailurePasses() {
        when(slack.findMembership("홍길동", EMAILS)).thenThrow(new BusinessException(ErrorCode.SLACK_USER_NOT_FOUND));

        assertThatCode(() -> gate(true).requireMember(user)).doesNotThrowAnyException();
        verifyNoInteractions(refusalNotice);
    }

    @Test
    @DisplayName("미리 물어볼 때는 막지도 메일을 보내지도 않고 답만 한다")
    void checkAnswersWithoutSideEffects() {
        when(slack.findMembership("홍길동", EMAILS)).thenReturn(Membership.NOT_FOUND);
        assertThat(gate(true).check(user)).isEqualTo(SlackMembershipGate.Verdict.NOT_MEMBER);

        when(slack.findMembership("홍길동", EMAILS)).thenReturn(Membership.CONFIRMED);
        assertThat(gate(true).check(user)).isEqualTo(SlackMembershipGate.Verdict.MEMBER);

        assertThat(gate(false).check(user)).isEqualTo(SlackMembershipGate.Verdict.UNCHECKED);
        verifyNoInteractions(refusalNotice);
    }

    @Test
    @DisplayName("미리 물어볼 때 Slack을 조회하지 못하면 확인하지 못했다고 답한다")
    void checkIsUncheckedOnLookupFailure() {
        when(slack.findMembership("홍길동", EMAILS)).thenThrow(new BusinessException(ErrorCode.SLACK_USER_NOT_FOUND));

        assertThat(gate(true).check(user)).isEqualTo(SlackMembershipGate.Verdict.UNCHECKED);
    }
}
