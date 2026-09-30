package DGU_AI_LAB.admin_be.global.util;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * 첫 증가 시점부터 정해진 시간 동안만 유지되는 Redis 횟수 카운터.
 * 증가와 첫 만료 설정을 한 스크립트로 한다. INCR 뒤 EXPIRE를 따로 부르면 그 사이에 프로세스가 죽을 때
 * 만료 없는 키가 남아, 그 키로 막히는 사용자(로그인 잠금·발송 제한 등)가 영구히 풀리지 않는다.
 */
@Component
@RequiredArgsConstructor
public class RedisWindowCounter {

    static final RedisScript<Long> INCREMENT_WITH_WINDOW = new DefaultRedisScript<>(
            "local n = redis.call('INCR', KEYS[1]) "
                    + "if n == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end "
                    + "return n",
            Long.class);

    private final RedisTemplate<String, String> redisTemplate;

    /** 키를 1 올리고 올린 뒤 값을 돌려준다. 키가 새로 생기면 window 뒤 사라진다. */
    public long increment(String key, Duration window) {
        Long count = redisTemplate.execute(INCREMENT_WITH_WINDOW, List.of(key), String.valueOf(window.toMillis()));
        if (count == null) {
            throw new IllegalStateException("Redis counter script returned no value for key " + key);
        }
        return count;
    }
}
