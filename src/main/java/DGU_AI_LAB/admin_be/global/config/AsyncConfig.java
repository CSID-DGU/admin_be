package DGU_AI_LAB.admin_be.global.config;

import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class AsyncConfig {

    /**
     * 승인 요청 후처리(계정 생성 → Pod 생성 → DB 반영 → 메일) 전용 executor.
     * corePoolSize=maxPoolSize=3, queueCapacity=0 — 이 이상 동시에 제출되면 큐잉하지 않고
     * AbortPolicy로 즉시 거부한다. 관리자 요청이 최대 10분(Pod 생성 대기)씩 쌓이는 대신,
     * 동시 3건을 넘는 제출은 곧바로 실패 처리해 명확한 에러를 준다 (기존 Semaphore(3)
     * fail-fast 정책과 동일한 사용자 체감 동작).
     */
    @Bean
    public ThreadPoolTaskExecutor approvalExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(3);
        executor.setMaxPoolSize(3);
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("approval-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        // 승인 처리 로그가 원래 요청을 처리하던 스레드와 같은 MDC 컨텍스트(요청 추적 정보 등)를
        // 유지하도록, 제출 시점의 MDC를 워커 스레드에 복사했다가 작업 종료 후 정리한다.
        executor.setTaskDecorator(new MdcTaskDecorator());
        executor.initialize();
        return executor;
    }

    private static class MdcTaskDecorator implements TaskDecorator {
        @Override
        public Runnable decorate(Runnable runnable) {
            Map<String, String> contextMap = MDC.getCopyOfContextMap();
            return () -> {
                Map<String, String> previous = MDC.getCopyOfContextMap();
                try {
                    if (contextMap != null) {
                        MDC.setContextMap(contextMap);
                    }
                    runnable.run();
                } finally {
                    if (previous != null) {
                        MDC.setContextMap(previous);
                    } else {
                        MDC.clear();
                    }
                }
            };
        }
    }
}
