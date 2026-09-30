package DGU_AI_LAB.admin_be.global.util;

import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;

/**
 * 인증 메일 발송 횟수 제한. 막지 않으면 같은 주소로 메일을 끝없이 보내게 만들 수 있고(메일 폭탄·발송 계정 평판),
 * 새 코드를 받을 때마다 입력 실패 횟수가 초기화돼 코드 대입 제한도 풀린다.
 * 주소 대소문자만 바꿔 제한을 피하지 못하게 소문자로 센다.
 */
@Component
@RequiredArgsConstructor
public class EmailSendThrottle {

    static final Duration COOLDOWN = Duration.ofSeconds(60);
    static final Duration WINDOW = Duration.ofHours(1);
    static final int MAX_SENDS_PER_WINDOW = 5;
    private static final String COOLDOWN_PREFIX = "email:send-cooldown:";
    private static final String COUNT_PREFIX = "email:send-count:";

    private final RedisTemplate<String, String> redisTemplate;
    private final RedisWindowCounter windowCounter;

    public void acquire(String email) {
        String key = email.toLowerCase(Locale.ROOT);
        if (!Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(COOLDOWN_PREFIX + key, "1", COOLDOWN))) {
            throw new BusinessException(ErrorCode.TOO_MANY_EMAIL_SENDS);
        }
        if (windowCounter.increment(COUNT_PREFIX + key, WINDOW) > MAX_SENDS_PER_WINDOW) {
            throw new BusinessException(ErrorCode.TOO_MANY_EMAIL_SENDS);
        }
    }
}
