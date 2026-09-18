package DGU_AI_LAB.admin_be.domain.scheduler;

import DGU_AI_LAB.admin_be.domain.alarm.dto.SlackMessageDto;
import DGU_AI_LAB.admin_be.domain.alarm.service.SlackApiService;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Consumer / Worker
 * Redis 큐에 쌓인 알림 요청을 하나씩 꺼내서 실제로 처리하는 Consumer입니다.
 * 전송 실패 시 최대 MAX_RETRY_COUNT회 재시도합니다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SlackNotificationWorker {

    private final RedisTemplate<String, Object> redisTemplate;
    private final SlackApiService slackApiService;
    private final ObjectMapper objectMapper;

    private static final String SLACK_QUEUE_KEY = "slack:notification:queue";
    private static final int MAX_RETRY_COUNT = 3;

    @Scheduled(fixedDelay = 1000)
    public void processSlackQueue() {
        Object messageObj = redisTemplate.opsForList().leftPop(SLACK_QUEUE_KEY);
        if (messageObj == null) return;

        SlackMessageDto dto;
        try {
            dto = objectMapper.convertValue(messageObj, SlackMessageDto.class);
        } catch (Exception e) {
            log.error("Slack 큐 메시지 역직렬화 실패 (폐기): {}", e.getMessage());
            return;
        }

        try {
            if (dto.getType() == SlackMessageDto.MessageType.WEBHOOK) {
                slackApiService.sendWebhook(dto.getWebhookUrl(), dto.getMessage());
                log.info("Slack Webhook 전송 성공 (Queue)");

            } else if (dto.getType() == SlackMessageDto.MessageType.DM) {
                slackApiService.sendDM(dto.getUsername(), dto.getEmail(), dto.getMessage());
                log.info("Slack DM 전송 성공 (Queue): {}", dto.getUsername());
            }

        } catch (BusinessException e) {
            if (e.getErrorCode() == ErrorCode.SLACK_CONFIG_DEAD) {
                // 웹훅이 삭제됐거나 봇 토큰이 폐기된 경우다. "유저 없음" 같은 일회성 비즈니스
                // 실패와 달리 이 채널이 살아있는 한 계속 반복된다 — WARN 한 줄로 묻히지 않게
                // ERROR로 격상해 로그 기반 모니터링이 잡을 수 있게 한다.
                log.error("[SLACK_CONFIG_DEAD] Slack 설정이 죽어 알림이 전달되지 않음, 즉시 확인 필요: {}", dto.getMessage());
                return;
            }
            // 그 외 비즈니스 예외(유저 없음 등)는 재시도해도 동일하게 실패하므로 바로 폐기
            log.warn("Slack 알림 처리 실패 (Business, 폐기): {}", e.getMessage());

        } catch (Exception e) {
            // 일시적 장애(네트워크, Slack API 다운 등)는 재시도
            requeue(dto, e);
        }
    }

    private void requeue(SlackMessageDto dto, Exception cause) {
        if (dto.getRetryCount() < MAX_RETRY_COUNT) {
            dto.incrementRetryCount();
            redisTemplate.opsForList().rightPush(SLACK_QUEUE_KEY, dto);
            log.warn("Slack 메시지 재시도 예약 ({}/{}회): {}", dto.getRetryCount(), MAX_RETRY_COUNT, cause.getMessage());
        } else {
            log.error("Slack 메시지 최대 재시도({}) 초과, 폐기: {} | 원인: {}",
                    MAX_RETRY_COUNT, dto.getMessage(), cause.getMessage());
        }
    }
}
