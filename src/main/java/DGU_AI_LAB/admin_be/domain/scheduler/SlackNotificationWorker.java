package DGU_AI_LAB.admin_be.domain.scheduler;

import DGU_AI_LAB.admin_be.domain.alarm.dto.SlackMessageDto;
import DGU_AI_LAB.admin_be.domain.alarm.service.SlackApiService;
import DGU_AI_LAB.admin_be.error.exception.BusinessException; // Import 확인
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SlackNotificationWorker {

    private final RedisTemplate<String, Object> redisTemplate;
    private final SlackApiService slackApiService;
    private final ObjectMapper objectMapper;

    private static final String SLACK_QUEUE_KEY = "slack:notification:queue";

    @Scheduled(fixedDelay = 1000)
    public void processSlackQueue() {
        try {
            Object messageObj = redisTemplate.opsForList().leftPop(SLACK_QUEUE_KEY);
            if (messageObj == null) return;

            SlackMessageDto dto = objectMapper.convertValue(messageObj, SlackMessageDto.class);

            if (dto.getType() == SlackMessageDto.MessageType.WEBHOOK) {
                slackApiService.sendWebhook(dto.getWebhookUrl(), dto.getMessage());
                log.info("Slack Webhook 전송 성공 (Queue)");

            } else if (dto.getType() == SlackMessageDto.MessageType.DM) {
                slackApiService.sendDM(dto.getUsername(), dto.getEmail(), dto.getMessage());
                log.info("Slack DM 전송 성공 (Queue): {}", dto.getUsername());
            }

        } catch (BusinessException e) {
            log.warn("Slack 알림 처리 실패 (Business): {}", e.getMessage());

        } catch (Exception e) {
            log.error("Slack 큐 처리 중 시스템 오류 (재시도 필요 시 큐 복귀 고려)", e);
        }
    }
}