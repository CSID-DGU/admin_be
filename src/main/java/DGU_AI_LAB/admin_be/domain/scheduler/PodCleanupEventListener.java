package DGU_AI_LAB.admin_be.domain.scheduler;

import DGU_AI_LAB.admin_be.domain.alarm.service.AlarmService;
import DGU_AI_LAB.admin_be.domain.scheduler.dto.PodCleanupTask;
import DGU_AI_LAB.admin_be.global.event.PodCleanupFailedEvent;
import DGU_AI_LAB.admin_be.global.util.MessageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class PodCleanupEventListener {

    private final AlarmService alarmService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final MessageUtils messageUtils;

    static final String POD_CLEANUP_QUEUE_KEY = "cleanup:pod:retry";

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePodCleanupFailed(PodCleanupFailedEvent event) {
        // 1. Slack 알림 (primary 안전망)
        try {
            String message = messageUtils.get("notification.pod.cleanup.registered",
                    event.podName(), event.username());
            alarmService.sendSlackAlert(message, null);
        } catch (Exception e) {
            log.warn("[Pod Cleanup] Slack 알림 전송 실패: pod={}", event.podName(), e);
        }

        // 2. Redis LPUSH (secondary best-effort retry buffer)
        try {
            PodCleanupTask task = PodCleanupTask.builder()
                    .podName(event.podName())
                    .username(event.username())
                    .retryCount(0)
                    .build();
            redisTemplate.opsForList().rightPush(POD_CLEANUP_QUEUE_KEY, task);
            log.info("[Pod Cleanup] 재시도 큐 등록: pod={}", event.podName());
        } catch (Exception e) {
            log.error("[Pod Cleanup] Redis 큐 등록 실패 - Slack 알림이 발송되었으므로 수동 대응 필요: pod={}",
                    event.podName(), e);
        }
    }
}
