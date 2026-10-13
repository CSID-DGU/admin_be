package DGU_AI_LAB.admin_be.domain.alarm.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 서버와 무관하게 하나씩만 있는 Slack 채널의 주소. 서버별 채널은 {@code ServerProfileRegistry}가 안다. */
@Component
public class NotificationChannels {

    @Value("${slack-webhook-url.noti}") // 사용자 알림 로그용
    private String notiLogWebhookUrl;

    /** 알림 기록 채널의 Slack 채널 ID(선택). 있으면 테스트용 계정의 신청서도 봇으로 올려 스레드 댓글을 단다. */
    @Value("${slack-channel-id.noti:}")
    private String notiChannelId;

    @Value("${slack-webhook-url.error-log}")
    private String errorLogWebhookUrl; // 중요한 에러 로그용

    public String notiWebhookUrl() {
        return notiLogWebhookUrl;
    }

    /** 설정하지 않았으면 null. */
    public String notiChannelId() {
        return notiChannelId == null || notiChannelId.isBlank() ? null : notiChannelId;
    }

    public String errorWebhookUrl() {
        return errorLogWebhookUrl;
    }
}
