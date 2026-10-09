package DGU_AI_LAB.admin_be.domain.scheduler;

import DGU_AI_LAB.admin_be.domain.alarm.SlackBlocks;
import DGU_AI_LAB.admin_be.domain.alarm.dto.SlackMessageDto;
import DGU_AI_LAB.admin_be.domain.alarm.service.SlackApiService;
import DGU_AI_LAB.admin_be.domain.requests.repository.ChangeRequestRepository;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

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
    private final RequestRepository requestRepository;
    private final ChangeRequestRepository changeRequestRepository;

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
            if (dto.getType() == SlackMessageDto.MessageType.CHANNEL && !postToChannel(dto)) {
                // 봇으로 못 올렸으면 webhook으로 대신 보낸다. 유형을 바꿔 두어 재시도 때 봇을 다시 거치지 않게 한다.
                dto.setType(SlackMessageDto.MessageType.WEBHOOK);
            }

            if (dto.getType() == SlackMessageDto.MessageType.WEBHOOK) {
                sendWebhook(dto);
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

    /**
     * 봇으로 채널에 올린다. 올렸으면 true. 실패는 여기서 삼키고 false를 돌려준다 — 봇이 채널에 없거나, 채널이 바뀌어
     * 스레드의 원래 메시지를 못 찾는 경우처럼 다시 해도 같은 실패가 대부분이라 호출부가 webhook으로 넘긴다.
     */
    private boolean postToChannel(SlackMessageDto dto) {
        String ts;
        try {
            List<Map<String, Object>> blocks = blocksOf(dto);
            ts = blocks.isEmpty()
                    ? slackApiService.postToChannel(dto.getChannelId(), dto.getMessage(), dto.getThreadTs())
                    : slackApiService.postToChannel(dto.getChannelId(), dto.getMessage(), dto.getThreadTs(), blocks);
        } catch (Exception e) {
            log.warn("Slack 봇 전송 실패, webhook으로 대신 보냄: {}", e.getMessage());
            return false;
        }
        log.info("Slack 채널 전송 성공 (Queue)");
        rememberMessage(dto, ts);
        return true;
    }

    /** 블록 양식으로 보내 보고, 그 전송이 실패하면 글만 다시 보낸다 — 양식 때문에 알림이 빠지지 않게 한다. */
    private void sendWebhook(SlackMessageDto dto) {
        List<Map<String, Object>> blocks = blocksOf(dto);
        if (!blocks.isEmpty()) {
            try {
                slackApiService.sendWebhook(dto.getWebhookUrl(), dto.getMessage(), blocks);
                return;
            } catch (Exception e) {
                log.warn("Slack 블록 양식 전송 실패, 글만 다시 보냄: {}", e.getMessage());
            }
        }
        slackApiService.sendWebhook(dto.getWebhookUrl(), dto.getMessage());
    }

    private static List<Map<String, Object>> blocksOf(SlackMessageDto dto) {
        return dto.isBlockLayout() ? SlackBlocks.fromMrkdwn(dto.getMessage()) : List.of();
    }

    /**
     * 올라간 신청서·변경 요청 접수 알림 메시지의 식별자를 그 신청·변경 요청에 적어 둔다. 못 적어도 이미 나간 알림은
     * 그대로 두고, 후속 알림만 일반 메시지로 간다.
     */
    private void rememberMessage(SlackMessageDto dto, String ts) {
        if (ts == null) {
            return;
        }
        try {
            if (dto.getRequestId() != null) {
                requestRepository.updateSlackMessageTs(dto.getRequestId(), ts);
            }
            if (dto.getChangeRequestId() != null) {
                changeRequestRepository.updateSlackMessageTs(dto.getChangeRequestId(), ts);
            }
        } catch (Exception e) {
            log.error("알림 메시지 식별자를 적지 못함: 요청 ID {}, 변경 요청 ID {}", dto.getRequestId(), dto.getChangeRequestId(), e);
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
