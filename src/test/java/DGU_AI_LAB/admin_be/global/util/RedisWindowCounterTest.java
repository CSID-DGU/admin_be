package DGU_AI_LAB.admin_be.global.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisWindowCounterTest {

    @InjectMocks
    private RedisWindowCounter counter;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Test
    @DisplayName("증가와 첫 만료 설정을 한 스크립트로 요청하고 증가한 값을 돌려준다")
    void incrementsWithWindowInOneScript() {
        when(redisTemplate.execute(RedisWindowCounter.INCREMENT_WITH_WINDOW, List.of("k"), "900000")).thenReturn(3L);

        assertThat(counter.increment("k", Duration.ofMinutes(15))).isEqualTo(3L);
    }

    @Test
    @DisplayName("스크립트가 값을 돌려주지 않으면 제한을 건너뛰지 않고 실패한다")
    void failsWhenScriptReturnsNothing() {
        when(redisTemplate.execute(RedisWindowCounter.INCREMENT_WITH_WINDOW, List.of("k"), "900000")).thenReturn(null);

        assertThatThrownBy(() -> counter.increment("k", Duration.ofMinutes(15)))
                .isInstanceOf(IllegalStateException.class);
    }
}
