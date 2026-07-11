package DGU_AI_LAB.admin_be.domain.alarm.service;

import DGU_AI_LAB.admin_be.domain.alarm.dto.SlackMessageDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 인프라 서버에서 호출하는 Slack 알림 큐잉 서비스.
 * 백엔드 알림 큐(slack:notification:queue)와 별도로 운영되어
 * 인프라 모니터링 알림과 백엔드 자동화 알림이 서로 지연을 주지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InfraAlarmService {

    public static final String INFRA_SLACK_QUEUE_KEY = "slack:infra:notification:queue";

    private final RedisTemplate<String, Object> redisTemplate;
    private final SlackApiService slackApiService;

    public void enqueue(String webhookUrl, String message) {
        SlackMessageDto dto = SlackMessageDto.builder()
                .type(SlackMessageDto.MessageType.WEBHOOK)
                .webhookUrl(webhookUrl)
                .message(message)
                .build();

        try {
            redisTemplate.opsForList().rightPush(INFRA_SLACK_QUEUE_KEY, dto);
            log.debug("인프라 Slack 큐 적재 완료");
        } catch (Exception e) {
            log.error("Redis 장애 — 인프라 Slack 직접 전송 시도: {}", e.getMessage());
            try {
                slackApiService.sendWebhook(webhookUrl, message);
                log.info("인프라 Slack 직접 전송 성공 (fallback)");
            } catch (Exception ex) {
                log.error("인프라 Slack 직접 전송도 실패: {}", ex.getMessage());
            }
        }
    }
}
