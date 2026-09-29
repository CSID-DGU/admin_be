package DGU_AI_LAB.admin_be.domain.groups.service;

import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 사용자별 그룹 생성 횟수 제한. 그룹 생성은 로그인한 누구나 부를 수 있고 한 번마다 디렉터리에 그룹을 만들며,
 * 그룹 번호(GID)는 재사용하지 않아 영구히 소모된다. 막지 않으면 반복 호출로 그룹과 GID를 끝없이 쓰게 만들 수 있다.
 */
@Component
@RequiredArgsConstructor
public class GroupCreateThrottle {

    static final Duration WINDOW = Duration.ofDays(1);
    static final int MAX_CREATES_PER_WINDOW = 5;
    private static final String COUNT_PREFIX = "group:create-count:";

    private final RedisTemplate<String, String> redisTemplate;

    public void acquire(Long userId) {
        String key = COUNT_PREFIX + userId;
        Long creates = redisTemplate.opsForValue().increment(key);
        if (creates != null && creates == 1) {
            redisTemplate.expire(key, WINDOW);
        }
        if (creates != null && creates > MAX_CREATES_PER_WINDOW) {
            throw new BusinessException(ErrorCode.TOO_MANY_GROUP_CREATIONS);
        }
    }
}
