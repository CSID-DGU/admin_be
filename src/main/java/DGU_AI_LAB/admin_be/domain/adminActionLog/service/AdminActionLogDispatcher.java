package DGU_AI_LAB.admin_be.domain.adminActionLog.service;

import DGU_AI_LAB.admin_be.domain.adminActionLog.dto.AdminActionEvent;

import jakarta.annotation.PreDestroy;

import lombok.extern.slf4j.Slf4j;

import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** 업무 요청에는 DB 대기와 재시도를 전파하지 않는다. 강제 종료 시 메모리 큐 유실 가능. */
@Slf4j
@Component
public class AdminActionLogDispatcher {
    private final AdminActionLogCommandService commandService;
    private final ThreadPoolExecutor executor =
            new ThreadPoolExecutor(
                    1,
                    1,
                    0,
                    TimeUnit.SECONDS,
                    new ArrayBlockingQueue<>(1000),
                    task -> {
                        Thread thread = new Thread(task, "admin-action-log");
                        thread.setDaemon(true);
                        return thread;
                    },
                    new ThreadPoolExecutor.AbortPolicy());

    public AdminActionLogDispatcher(AdminActionLogCommandService commandService) {
        this.commandService = commandService;
    }

    public void submit(AdminActionEvent event) {
        try {
            executor.execute(() -> persist(event));
        } catch (RejectedExecutionException ex) {
            failed(event, 0, "QUEUE_REJECTED");
        }
    }

    private void persist(AdminActionEvent event) {
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                commandService.save(event);
                return;
            } catch (RuntimeException ex) {
                if ((!(ex instanceof TransientDataAccessException)
                                && !(ex instanceof DataAccessResourceFailureException))
                        || attempt == 3) {
                    failed(event, attempt, ex.getClass().getSimpleName());
                    return;
                }
                try {
                    TimeUnit.MILLISECONDS.sleep(200L * attempt);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    failed(event, attempt, "INTERRUPTED");
                    return;
                }
            }
        }
    }

    private void failed(AdminActionEvent event, int attempts, String reason) {
        // 본문·대상 문자열·예외 메시지를 출력하지 않는다. 잘못된 입력에 비밀 값이 있을 수 있다.
        log.error(
                "Admin audit persistence failed: eventId={}, adminId={}, action={}, occurredAt={},"
                    + " httpStatus={}, attempts={}, reason={}",
                event.eventId(),
                event.adminId(),
                event.actionType(),
                event.occurredAt(),
                event.httpStatus(),
                attempts,
                reason);
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                log.error(
                        "Admin audit shutdown timed out: queued={}, active={}",
                        executor.getQueue().size(),
                        executor.getActiveCount());
                executor.shutdownNow();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.error("Admin audit shutdown interrupted: queued={}", executor.getQueue().size());
            executor.shutdownNow();
        }
    }
}
