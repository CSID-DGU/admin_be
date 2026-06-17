package DGU_AI_LAB.admin_be.domain.scheduler;

import DGU_AI_LAB.admin_be.domain.alarm.service.AlarmService;
import DGU_AI_LAB.admin_be.domain.requests.service.PodService;
import DGU_AI_LAB.admin_be.domain.scheduler.dto.PodCleanupTask;
import DGU_AI_LAB.admin_be.global.util.MessageUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PodCleanupWorker {

    private final RedisTemplate<String, Object> redisTemplate;
    private final PodService podService;
    private final AlarmService alarmService;
    private final ObjectMapper objectMapper;
    private final MessageUtils messageUtils;

    private static final String POD_CLEANUP_QUEUE_KEY = "cleanup:pod:retry";

    @Scheduled(fixedDelay = 600_000)
    public void processCleanupQueue() {
        Object taskObj = redisTemplate.opsForList().leftPop(POD_CLEANUP_QUEUE_KEY);
        if (taskObj == null) return;

        PodCleanupTask task = objectMapper.convertValue(taskObj, PodCleanupTask.class);
        log.info("[Pod Cleanup] 재시도 시작: pod={}, retry={}/{}", task.getPodName(), task.getRetryCount() + 1, PodCleanupTask.MAX_RETRIES);

        try {
            podService.deletePod(task.getPodName());
            log.info("[Pod Cleanup] 재시도 성공: pod={}", task.getPodName());
        } catch (Exception e) {
            task.incrementRetry();

            if (task.isRetryExhausted()) {
                log.error("[Pod Cleanup] 최대 재시도 초과 - 수동 개입 필요: pod={}", task.getPodName(), e);
                sendManualInterventionAlert(task);
            } else {
                log.warn("[Pod Cleanup] 재시도 실패, 큐 재등록: pod={}, nextRetry={}/{}",
                        task.getPodName(), task.getRetryCount() + 1, PodCleanupTask.MAX_RETRIES);
                try {
                    redisTemplate.opsForList().rightPush(POD_CLEANUP_QUEUE_KEY, task);
                } catch (Exception redisEx) {
                    log.error("[Pod Cleanup] Redis 재등록 실패 - 수동 개입 필요: pod={}", task.getPodName(), redisEx);
                    sendManualInterventionAlert(task);
                }
            }
        }
    }

    private void sendManualInterventionAlert(PodCleanupTask task) {
        try {
            String message = messageUtils.get("notification.pod.cleanup.exhausted",
                    task.getPodName(), task.getUsername(), task.getRetryCount());
            alarmService.sendSlackAlert(message, null);
        } catch (Exception e) {
            log.error("[Pod Cleanup] 수동 개입 알림 전송 실패: pod={}", task.getPodName(), e);
        }
    }
}
