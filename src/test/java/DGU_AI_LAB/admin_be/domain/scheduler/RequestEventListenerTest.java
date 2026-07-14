package DGU_AI_LAB.admin_be.domain.scheduler;

import DGU_AI_LAB.admin_be.domain.alarm.service.AlarmService;
import DGU_AI_LAB.admin_be.global.event.RequestExpiredEvent;
import DGU_AI_LAB.admin_be.global.util.MessageUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RequestEventListenerTest {

    @InjectMocks
    private RequestEventListener requestEventListener;

    @Mock
    private AlarmService alarmService;

    @Mock
    private MessageUtils messageUtils;

    private static final RequestExpiredEvent EVENT = new RequestExpiredEvent(
            "홍길동", "hong@test.com", "user1", "server-lab",
            "pod-xxx", "ssh(30022)", "2025-12-31");

    @Test
    @DisplayName("이벤트에 포함된 userName/userEmail로 사용자 알림을 전송한다")
    void handleExpiredEvent_sendsUserNotificationWithStringFields() {
        requestEventListener.handleExpiredEvent(EVENT);

        verify(alarmService).sendAllAlerts(eq("홍길동"), eq("hong@test.com"), any(), any());
    }

    @Test
    @DisplayName("관리자 알림은 serverName으로 전송한다")
    void handleExpiredEvent_sendsAdminNotificationWithServerName() {
        requestEventListener.handleExpiredEvent(EVENT);

        verify(alarmService).sendAdminSlackNotification(eq("server-lab"), any());
    }

    @Test
    @DisplayName("알림 전송 실패 시 예외가 전파되지 않는다")
    void handleExpiredEvent_doesNotPropagateException_whenAlarmFails() {
        doThrow(new RuntimeException("slack error")).when(alarmService).sendAllAlerts(any(), any(), any(), any());

        requestEventListener.handleExpiredEvent(EVENT);

        verify(alarmService, times(1)).sendAllAlerts(any(), any(), any(), any());
    }
}
