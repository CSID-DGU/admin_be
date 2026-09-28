package DGU_AI_LAB.admin_be.global.util;

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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailSendThrottleTest {

    @InjectMocks
    private EmailSendThrottle throttle;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("대기 시간 안에 다시 보내면 거절하고 발송 횟수는 세지 않는다")
    void rejectsWithinCooldown() {
        when(valueOperations.setIfAbsent("email:send-cooldown:a@dgu.ac.kr", "1", EmailSendThrottle.COOLDOWN)).thenReturn(false);

        assertThatThrownBy(() -> throttle.acquire("A@dgu.ac.kr")).isInstanceOf(BusinessException.class);
        verify(valueOperations, never()).increment(anyString());
    }

    @Test
    @DisplayName("첫 발송은 한 시간 창을 열고 통과한다")
    void firstSendOpensWindow() {
        when(valueOperations.setIfAbsent("email:send-cooldown:a@dgu.ac.kr", "1", EmailSendThrottle.COOLDOWN)).thenReturn(true);
        when(valueOperations.increment("email:send-count:a@dgu.ac.kr")).thenReturn(1L);

        assertThatCode(() -> throttle.acquire("a@dgu.ac.kr")).doesNotThrowAnyException();
        verify(redisTemplate).expire("email:send-count:a@dgu.ac.kr", EmailSendThrottle.WINDOW);
    }

    @Test
    @DisplayName("한 시간에 허용한 횟수를 넘으면 거절한다")
    void rejectsOverHourlyLimit() {
        when(valueOperations.setIfAbsent("email:send-cooldown:a@dgu.ac.kr", "1", EmailSendThrottle.COOLDOWN)).thenReturn(true);
        when(valueOperations.increment("email:send-count:a@dgu.ac.kr"))
                .thenReturn((long) EmailSendThrottle.MAX_SENDS_PER_WINDOW + 1);

        assertThatThrownBy(() -> throttle.acquire("a@dgu.ac.kr")).isInstanceOf(BusinessException.class);
    }
}
