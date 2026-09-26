package DGU_AI_LAB.admin_be.global.alert;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RedisAlertDeduplicator")
class RedisAlertDeduplicatorTest {

    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> ops;

    private RedisAlertDeduplicator dedup;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(ops);
        dedup = new RedisAlertDeduplicator(redis, 168);
    }

    @Test
    @DisplayName("처음 보는 사건이면 접두어를 붙인 키를 만료 시간과 함께 남기고 알리게 한다")
    void firstTime() {
        when(ops.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);

        assertThat(dedup.firstOccurrence("job-unresolved:revoke:41:900")).isTrue();
        verify(ops).setIfAbsent(eq("alert-once:job-unresolved:revoke:41:900"), eq("1"), eq(Duration.ofHours(168)));
    }

    @Test
    @DisplayName("이미 남아 있는 사건이면 알리지 않게 한다")
    void alreadySeen() {
        when(ops.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);

        assertThat(dedup.firstOccurrence("job-unresolved:revoke:41:900")).isFalse();
    }

    @Test
    @DisplayName("Redis에 닿지 못하면 알리게 한다 — 빠뜨리느니 중복이 낫다")
    void storeUnavailable() {
        when(ops.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenThrow(new RedisConnectionFailureException("down"));

        assertThat(dedup.firstOccurrence("job-unresolved:revoke:41:900")).isTrue();
    }
}
