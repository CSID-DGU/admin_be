package DGU_AI_LAB.admin_be.domain.adminActionLog;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadPoolExecutor;

@Slf4j
@Component
public class AdminActionLogWriter {

    private final AdminActionLogRepository repository;
    private final ThreadPoolTaskExecutor executor;

    public AdminActionLogWriter(AdminActionLogRepository repository) {
        this.repository = repository;
        this.executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("admin-action-log-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(5);
        executor.initialize();
    }

    public void submit(AdminActionLog entry) {
        try {
            executor.execute(() -> save(entry));
        } catch (TaskRejectedException e) {
            logFailure("queue rejected", entry, e);
        }
    }

    private void save(AdminActionLog entry) {
        try {
            repository.save(entry);
        } catch (RuntimeException e) {
            logFailure("database write failed", entry, e);
        }
    }

    private void logFailure(String reason, AdminActionLog entry, RuntimeException e) {
        log.error("Admin action log {}: adminId={} action={} targetId={} status={} occurredAt={}",
                reason, entry.getAdminId(), entry.getActionType(),
                entry.getTargetId(), entry.getHttpStatus(), entry.getOccurredAt(), e);
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }
}