package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import DGU_AI_LAB.admin_be.global.util.RedisWindowCounter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 사용자별 본인 컨테이너 재시작 횟수 제한. 재시작 한 번은 노드에서 이미지를 굽고 Pod를 새로 만드는 작업이라,
 * 막지 않으면 반복 호출로 노드 디스크와 작업 대기열을 채울 수 있다. 관리자가 하는 재시작에는 적용하지 않는다.
 */
@Component
@RequiredArgsConstructor
public class ContainerRestartThrottle {

    static final Duration WINDOW = Duration.ofHours(1);
    static final int MAX_RESTARTS_PER_WINDOW = 5;
    private static final String COUNT_PREFIX = "request:restart-count:";

    private final RedisWindowCounter windowCounter;

    public void acquire(Long userId) {
        if (windowCounter.increment(COUNT_PREFIX + userId, WINDOW) > MAX_RESTARTS_PER_WINDOW) {
            throw new BusinessException(ErrorCode.TOO_MANY_CONTAINER_RESTARTS);
        }
    }
}
