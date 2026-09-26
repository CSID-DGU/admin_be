package DGU_AI_LAB.admin_be.global.alert;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis에 사건 키를 만료 시간과 함께 남겨 알림 중복을 막는다. 메모리에 두면 admin_be가 재시작될 때 같은 알림이 다시
 * 나가고, 레플리카가 여럿이면 레플리카 수만큼 나가며, 상태가 다른 경로로 바뀐 사건의 기록이 쌓이기만 한다.
 */
@Slf4j
@Component
public class RedisAlertDeduplicator implements AlertDeduplicator {

    static final String KEY_PREFIX = "alert-once:";

    private final StringRedisTemplate redis;
    private final Duration ttl;

    public RedisAlertDeduplicator(StringRedisTemplate redis,
                                  @Value("${alerts.dedup-ttl-hours:168}") long ttlHours) {
        this.redis = redis;
        this.ttl = Duration.ofHours(ttlHours);
    }

    @Override
    public boolean firstOccurrence(String eventKey) {
        try {
            return !Boolean.FALSE.equals(redis.opsForValue().setIfAbsent(KEY_PREFIX + eventKey, "1", ttl));
        } catch (Exception e) {
            log.warn("알림 중복 기록 저장소에 닿지 못해 알림을 그대로 보낸다: key={}", eventKey, e);
            return true;
        }
    }
}
