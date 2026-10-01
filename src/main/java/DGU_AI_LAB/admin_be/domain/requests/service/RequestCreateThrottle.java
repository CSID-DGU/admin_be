package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import DGU_AI_LAB.admin_be.global.util.RedisWindowCounter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 사용자별 컨테이너 신청 횟수 제한. 신청은 로그인한 누구나 부를 수 있고 한 건마다 관리자 검토 대기열과
 * Slack 알림을 만든다. 막지 않으면 반복 호출로 대기열과 알림 채널을 채울 수 있다.
 * 첫 신청부터 24시간 동안 세므로 취소하거나 거절된 신청도 횟수에 든다.
 */
@Component
@RequiredArgsConstructor
public class RequestCreateThrottle {

    static final Duration WINDOW = Duration.ofDays(1);
    static final int MAX_CREATES_PER_WINDOW = 5;
    private static final String COUNT_PREFIX = "request:create-count:";

    private final RedisWindowCounter windowCounter;

    public void acquire(Long userId) {
        if (windowCounter.increment(COUNT_PREFIX + userId, WINDOW) > MAX_CREATES_PER_WINDOW) {
            throw new BusinessException(ErrorCode.TOO_MANY_CONTAINER_REQUESTS);
        }
    }
}
