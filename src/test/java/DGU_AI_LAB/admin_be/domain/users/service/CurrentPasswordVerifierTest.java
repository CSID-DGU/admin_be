package DGU_AI_LAB.admin_be.domain.users.service;

import DGU_AI_LAB.admin_be.domain.users.entity.User;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrentPasswordVerifierTest {

    private static final String KEY = "PW_CONFIRM_FAIL:7";

    @InjectMocks
    private CurrentPasswordVerifier verifier;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> ops;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder().email("a@dgu.ac.kr").password("encoded").name("n")
                .studentId("1").phone("010").department("d").build();
        ReflectionTestUtils.setField(user, "userId", 7L);
        when(redisTemplate.opsForValue()).thenReturn(ops);
    }

    @Test
    @DisplayName("맞으면 실패 횟수를 지운다")
    void clearsCounterOnSuccess() {
        when(passwordEncoder.matches("pw", "encoded")).thenReturn(true);

        verifier.verify(user, "pw");

        verify(redisTemplate).delete(KEY);
    }

    @Test
    @DisplayName("첫 실패는 횟수를 올리고 15분 만료를 건다")
    void countsFailureWithExpiry() {
        when(passwordEncoder.matches("bad", "encoded")).thenReturn(false);
        when(ops.increment(KEY)).thenReturn(1L);

        assertThatThrownBy(() -> verifier.verify(user, "bad"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_PASSWORD);
        verify(redisTemplate).expire(KEY, CurrentPasswordVerifier.LOCKOUT_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("5번 틀린 뒤에는 맞는 비밀번호도 확인하지 않고 막는다")
    void locksAfterMaxAttempts() {
        when(ops.get(KEY)).thenReturn(String.valueOf(CurrentPasswordVerifier.MAX_ATTEMPTS));

        assertThatThrownBy(() -> verifier.verify(user, "pw"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.TOO_MANY_PASSWORD_ATTEMPTS);
        verify(passwordEncoder, never()).matches("pw", "encoded");
    }
}
