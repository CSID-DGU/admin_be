package DGU_AI_LAB.admin_be.domain.alarm.service;

import DGU_AI_LAB.admin_be.domain.alarm.dto.SlackMessageDto;
import DGU_AI_LAB.admin_be.global.util.MessageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Slack으로 나갈 글을 전송 큐에 싣는다. 실제 전송은 큐를 읽는 작업자가 한다. 무엇을 왜 보내는지는 모른다 —
 * 큐가 죽었을 때 직접 보내 전달만 지키는 것까지가 여기 일이다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SlackNotificationQueue {

    private static final String SLACK_QUEUE_KEY = "slack:notification:queue";

    private final RedisTemplate<String, Object> redisTemplate;
    private final SlackApiService slackApiService;
    private final MessageUtils messageUtils;
    private final DmRecipientEmails dmRecipientEmails;
    private final NotificationChannels channels;

    public void webhook(String message, String webhookUrl) {
        webhook(message, webhookUrl, false);
    }

    public void webhook(String message, String webhookUrl, boolean blockLayout) {
        push(SlackMessageDto.builder()
                .type(SlackMessageDto.MessageType.WEBHOOK)
                .webhookUrl(webhookUrl)
                .message(message)
                .blockLayout(blockLayout)
                .build());
    }

    public void dm(String username, String email, String message) {
        push(SlackMessageDto.builder()
                .type(SlackMessageDto.MessageType.DM)
                .username(username)
                .email(email)
                .message(message)
                .build());
    }

    public void push(SlackMessageDto dto) {
        try {
            redisTemplate.opsForList().rightPush(SLACK_QUEUE_KEY, dto);
            log.debug("Slack 큐 적재: {}", dto.getType());
        } catch (Exception e) {
            log.error("⚠️ Redis 장애! 직접 전송 시도. ({})", e.getMessage());
            handleFallbackDirectSend(dto);
        }
    }

    private void handleFallbackDirectSend(SlackMessageDto dto) {
        String notice = messageUtils.get("notification.error.redis-fallback");
        String fullMessage = dto.getMessage() + notice;

        try {
            if (dto.getType() == SlackMessageDto.MessageType.DM) {
                slackApiService.sendDM(dto.getUsername(), dmRecipientEmails.of(dto.getEmail()), fullMessage);
            } else {
                // 봇으로 올릴 글(CHANNEL)도 큐가 죽은 동안에는 webhook으로 보낸다 — 스레드는 포기하고 전달만 지킨다.
                slackApiService.sendWebhook(dto.getWebhookUrl(), fullMessage);
            }

            // Fallback이 작동했다는 건 시스템이 불안정하다는 뜻이므로 에러 로그 채널에 알립니다.
            String webhookUrl = dto.getWebhookUrl();
            if (webhookUrl == null || !webhookUrl.equals(channels.errorWebhookUrl())) {
                slackApiService.sendWebhook(channels.errorWebhookUrl(), "⚠️ Redis 장애 발생 (Direct Send 작동됨)");
            }

            log.info("✅ Fallback 직접 전송 성공");
        } catch (Exception ex) {
            log.error("❌ Fallback 실패", ex);
        }
    }
}
