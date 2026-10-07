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
import static org.mockito.Mockito.never;
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

    @Test
    @DisplayName("이어지는 사건은 다시 볼 때마다 기록 수명을 늘리고 알리지 않게 한다")
    void lastingEventExtendsRecord() {
        when(ops.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);

        assertThat(dedup.firstOccurrenceWhileItLasts("container-stopped:7:p")).isFalse();
        verify(redis).expire("alert-once:container-stopped:7:p", Duration.ofHours(168));
    }

    @Test
    @DisplayName("이어지는 사건도 처음 보면 알리게 하고 수명은 건드리지 않는다")
    void lastingEventFirstTime() {
        when(ops.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);

        assertThat(dedup.firstOccurrenceWhileItLasts("container-stopped:7:p")).isTrue();
        verify(redis, never()).expire(anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("수명을 늘리지 못해도 이미 알린 사건은 다시 알리지 않게 한다")
    void lastingEventExpireFails() {
        when(ops.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);
        when(redis.expire(anyString(), any(Duration.class))).thenThrow(new RedisConnectionFailureException("down"));

        assertThat(dedup.firstOccurrenceWhileItLasts("container-stopped:7:p")).isFalse();
    }
}
