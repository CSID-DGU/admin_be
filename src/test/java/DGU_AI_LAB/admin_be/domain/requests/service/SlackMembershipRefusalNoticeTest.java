package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.domain.alarm.service.AlarmService;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.global.util.RedisWindowCounter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("SlackMembershipRefusalNotice")
class SlackMembershipRefusalNoticeTest {

    private final RedisWindowCounter windowCounter = mock(RedisWindowCounter.class);
    private final AlarmService alarmService = mock(AlarmService.class);
    private final SlackMembershipRefusalNotice notice = new SlackMembershipRefusalNotice(windowCounter, alarmService);

    private static User user() {
        User user = mock(User.class);
        when(user.getUserId()).thenReturn(7L);
        when(user.getName()).thenReturn("홍길동");
        when(user.getEmail()).thenReturn("hong@dgu.ac.kr");
        return user;
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("간격 안의 첫 거절에만 학교 이메일로 안내를 보낸다")
    void mailsOncePerInterval() {
        when(windowCounter.increment("request:slack-refusal-mail:7", SlackMembershipRefusalNotice.INTERVAL))
                .thenReturn(1L, 2L);

        notice.send(user());
        notice.send(user());

        verify(alarmService, times(1)).sendSlackMembershipRequiredEmail("홍길동", "hong@dgu.ac.kr");
    }

    @Test
    @DisplayName("신청 트랜잭션 안에서는 보내지 않고, 롤백으로 끝난 뒤에 보낸다")
    void waitsUntilTransactionEnds() {
        when(windowCounter.increment(anyString(), eq(SlackMembershipRefusalNotice.INTERVAL))).thenReturn(1L);
        TransactionSynchronizationManager.initSynchronization();

        notice.send(user());
        verify(alarmService, never()).sendSlackMembershipRequiredEmail(anyString(), anyString());

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verify(alarmService).sendSlackMembershipRequiredEmail("홍길동", "hong@dgu.ac.kr");
    }

    @Test
    @DisplayName("횟수를 세지 못해도 예외를 올리지 않는다")
    void counterFailureIsSwallowed() {
        when(windowCounter.increment(anyString(), eq(SlackMembershipRefusalNotice.INTERVAL)))
                .thenThrow(new IllegalStateException("redis down"));

        notice.send(user());

        verify(alarmService, never()).sendSlackMembershipRequiredEmail(anyString(), anyString());
    }
}
