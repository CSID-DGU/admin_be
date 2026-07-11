package DGU_AI_LAB.admin_be.domain.scheduler;

import DGU_AI_LAB.admin_be.domain.alarm.dto.SlackMessageDto;
import DGU_AI_LAB.admin_be.domain.alarm.service.InfraAlarmService;
import DGU_AI_LAB.admin_be.domain.alarm.service.SlackApiService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 인프라 Slack 알림 큐 Consumer.
 * 1초마다 slack:infra:notification:queue에서 메시지를 하나 꺼내 Webhook 전송.
 * 채널당 초당 1회 Slack 제한을 준수하며, 백엔드 알림 큐와 완전히 독립 운영.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InfraSlackNotificationWorker {

    private final RedisTemplate<String, Object> redisTemplate;
    private final SlackApiService slackApiService;
    private final ObjectMapper objectMapper;

    private static final int MAX_RETRY_COUNT = 3;

    @Scheduled(fixedDelay = 1000)
    public void processInfraSlackQueue() {
        Object messageObj = redisTemplate.opsForList().leftPop(InfraAlarmService.INFRA_SLACK_QUEUE_KEY);
        if (messageObj == null) return;

        SlackMessageDto dto;
        try {
            dto = objectMapper.convertValue(messageObj, SlackMessageDto.class);
        } catch (Exception e) {
            log.error("인프라 Slack 큐 메시지 역직렬화 실패 (폐기): {}", e.getMessage());
            return;
        }

        try {
            slackApiService.sendWebhook(dto.getWebhookUrl(), dto.getMessage());
            log.info("인프라 Slack Webhook 전송 성공");
        } catch (Exception e) {
            requeue(dto, e);
        }
    }

    private void requeue(SlackMessageDto dto, Exception cause) {
        if (dto.getRetryCount() < MAX_RETRY_COUNT) {
            dto.incrementRetryCount();
            redisTemplate.opsForList().rightPush(InfraAlarmService.INFRA_SLACK_QUEUE_KEY, dto);
            log.warn("인프라 Slack 재시도 예약 ({}/{}회): {}", dto.getRetryCount(), MAX_RETRY_COUNT, cause.getMessage());
        } else {
            log.error("인프라 Slack 최대 재시도({}) 초과, 폐기: {} | 원인: {}",
                    MAX_RETRY_COUNT, dto.getMessage(), cause.getMessage());
        }
    }
}
