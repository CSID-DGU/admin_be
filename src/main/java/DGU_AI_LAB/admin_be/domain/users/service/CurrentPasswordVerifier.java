package DGU_AI_LAB.admin_be.domain.users.service;

import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 비밀번호 변경 폼의 "현재 웹 비밀번호" 확인. 로그인과 같은 규칙(15분 안에 5번 틀리면 잠금)을 둔다 —
 * 탈취된 토큰으로 웹 비밀번호를 무차별 대입하거나, 그것으로 Ubuntu 비밀번호를 바꾸지 못하게 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CurrentPasswordVerifier {

    static final int MAX_ATTEMPTS = 5;
    static final long LOCKOUT_SECONDS = 900;

    private final PasswordEncoder passwordEncoder;
    private final RedisTemplate<String, String> redisTemplate;

    public void verify(User user, String rawPassword) {
        String key = "PW_CONFIRM_FAIL:" + user.getUserId();
        String attempts = redisTemplate.opsForValue().get(key);
        if (attempts != null && Integer.parseInt(attempts) >= MAX_ATTEMPTS) {
            throw new BusinessException(ErrorCode.TOO_MANY_PASSWORD_ATTEMPTS);
        }
        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, LOCKOUT_SECONDS, TimeUnit.SECONDS);
            }
            log.warn("[CurrentPasswordVerifier] userId={} 현재 비밀번호 불일치 ({}회)", user.getUserId(), count);
            throw new BusinessException(ErrorCode.INVALID_PASSWORD);
        }
        redisTemplate.delete(key);
    }
}
