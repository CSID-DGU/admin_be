package DGU_AI_LAB.admin_be.domain.groups.service;

import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import DGU_AI_LAB.admin_be.global.util.RedisWindowCounter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupCreateThrottleTest {

    private static final String KEY = "group:create-count:7";

    @InjectMocks
    private GroupCreateThrottle throttle;

    @Mock
    private RedisWindowCounter windowCounter;

    @Test
    @DisplayName("하루 창으로 생성 횟수를 세고 한도까지는 통과한다")
    void allowsUpToLimit() {
        when(windowCounter.increment(KEY, GroupCreateThrottle.WINDOW))
                .thenReturn((long) GroupCreateThrottle.MAX_CREATES_PER_WINDOW);

        assertThatCode(() -> throttle.acquire(7L)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("하루 한도를 넘으면 429로 거절한다")
    void rejectsOverDailyLimit() {
        when(windowCounter.increment(KEY, GroupCreateThrottle.WINDOW))
                .thenReturn(GroupCreateThrottle.MAX_CREATES_PER_WINDOW + 1L);

        assertThatThrownBy(() -> throttle.acquire(7L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.TOO_MANY_GROUP_CREATIONS);
    }
}
