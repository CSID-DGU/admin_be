package DGU_AI_LAB.admin_be.domain.groups.service;

import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupCreateThrottleTest {

    @InjectMocks
    private GroupCreateThrottle throttle;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("첫 생성은 하루 창을 열고 통과한다")
    void firstCreateOpensWindow() {
        when(valueOperations.increment("group:create-count:7")).thenReturn(1L);

        assertThatCode(() -> throttle.acquire(7L)).doesNotThrowAnyException();
        verify(redisTemplate).expire("group:create-count:7", GroupCreateThrottle.WINDOW);
    }

    @Test
    @DisplayName("한도까지는 통과하고 창을 다시 열지 않는다")
    void allowsUpToLimit() {
        when(valueOperations.increment("group:create-count:7")).thenReturn((long) GroupCreateThrottle.MAX_CREATES_PER_WINDOW);

        assertThatCode(() -> throttle.acquire(7L)).doesNotThrowAnyException();
        verify(redisTemplate, never()).expire(anyString(), any(java.time.Duration.class));
    }

    @Test
    @DisplayName("하루 한도를 넘으면 429로 거절한다")
    void rejectsOverDailyLimit() {
        when(valueOperations.increment("group:create-count:7")).thenReturn(GroupCreateThrottle.MAX_CREATES_PER_WINDOW + 1L);

        assertThatThrownBy(() -> throttle.acquire(7L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TOO_MANY_GROUP_CREATIONS);
    }
}
