package DGU_AI_LAB.admin_be.domain.alarm.service;

import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.global.server.ServerProfileRegistry;
import DGU_AI_LAB.admin_be.global.util.MessageUtils;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RequestChannelNotifierTestPodTest {

    private final MessageUtils messages = mock(MessageUtils.class);
    private final ServerProfileRegistry servers = mock(ServerProfileRegistry.class);
    private final SlackNotificationQueue queue = mock(SlackNotificationQueue.class);
    private final NotificationChannels channels = mock(NotificationChannels.class);
    private final RequestChannelNotifier notifier = new RequestChannelNotifier(messages, servers, queue, channels);

    private Request request(boolean test) {
        Request r = mock(Request.class);
        User u = mock(User.class);
        ResourceGroup rg = mock(ResourceGroup.class);
        when(u.getEmail()).thenReturn("admin@dgu.ac.kr");
        when(u.getName()).thenReturn("관리자");
        when(rg.getServerName()).thenReturn("FARM");
        when(r.getUser()).thenReturn(u);
        when(r.getResourceGroup()).thenReturn(rg);
        when(r.isTest()).thenReturn(test);
        when(r.getRequestId()).thenReturn(1L);
        when(messages.get(any(), any(Object[].class))).thenReturn("msg");
        when(servers.requestWebhookUrl("FARM")).thenReturn(Optional.of("https://farm-request"));
        when(channels.notiWebhookUrl()).thenReturn("https://noti");
        return r;
    }

    @Test
    void 테스트_신청의_거절_알림은_알림_기록_채널로만_간다() {
        Request r = request(true);

        notifier.sendRequestRejectedNotification(r, "사유");

        verify(queue).webhook(eq("msg"), eq("https://noti"), anyBoolean());
        verify(queue, never()).webhook(any(), eq("https://farm-request"), anyBoolean());
    }

    @Test
    void 실_신청의_거절_알림은_서버_신청서_채널로_간다() {
        Request r = request(false);

        notifier.sendRequestRejectedNotification(r, "사유");

        verify(queue).webhook(eq("msg"), eq("https://farm-request"), anyBoolean());
    }
}
