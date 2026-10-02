package DGU_AI_LAB.admin_be.domain.groups.service;

import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import DGU_AI_LAB.admin_be.global.util.RedisWindowCounter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 사용자별 그룹 생성 횟수 제한. 그룹 생성은 로그인한 누구나 부를 수 있다. 만든 그룹은 승인 전까지 DB 에만 있어
 * 인프라 자원이나 GID 를 쓰지 않지만, 모든 사용자의 신청 화면 목록에 보이므로 반복 호출로 목록을 채우지 못하게 막는다.
 */
@Component
@RequiredArgsConstructor
public class GroupCreateThrottle {

    static final Duration WINDOW = Duration.ofDays(1);
    static final int MAX_CREATES_PER_WINDOW = 5;
    private static final String COUNT_PREFIX = "group:create-count:";

    private final RedisWindowCounter windowCounter;

    public void acquire(Long userId) {
        if (windowCounter.increment(COUNT_PREFIX + userId, WINDOW) > MAX_CREATES_PER_WINDOW) {
            throw new BusinessException(ErrorCode.TOO_MANY_GROUP_CREATIONS);
        }
    }
}
