package DGU_AI_LAB.admin_be.domain.scheduler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("InactivityNoticeStore")
class InactivityNoticeStoreTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 1, 10);

    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> valueOps;
    @InjectMocks private InactivityNoticeStore store;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(valueOps);
    }

    @Test
    @DisplayName("알린 예정일을 돌려주고, 기록이 없으면 빈 값이다")
    void findsDeadline() {
        when(valueOps.get("user-inactive-notice:1")).thenReturn("2026-01-17");

        assertThat(store.findDeadline(1L)).contains(LocalDate.of(2026, 1, 17));
        assertThat(store.findDeadline(2L)).isEmpty();
    }

    @Test
    @DisplayName("Redis에 닿지 못하면 조회는 예외를 그대로 던진다")
    void findPropagatesRedisFailure() {
        when(valueOps.get(anyString())).thenThrow(new IllegalStateException("Redis down"));

        assertThatThrownBy(() -> store.findDeadline(1L)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("예정일까지 남은 날에 3일을 더한 만큼만 기록을 둔다")
    void savesWithExpiryAfterDeadline() {
        store.save(1L, TODAY.plusDays(7), TODAY);

        verify(valueOps).set("user-inactive-notice:1", "2026-01-17", Duration.ofDays(10));
    }

    @Test
    @DisplayName("기록·삭제 실패는 삼킨다")
    void swallowsWriteFailures() {
        doThrow(new IllegalStateException("Redis down")).when(valueOps).set(anyString(), anyString(), any(Duration.class));
        when(redis.delete(anyString())).thenThrow(new IllegalStateException("Redis down"));

        assertThatCode(() -> store.save(1L, TODAY, TODAY)).doesNotThrowAnyException();
        assertThatCode(() -> store.forget(1L)).doesNotThrowAnyException();
    }
}
