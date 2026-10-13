package DGU_AI_LAB.admin_be.domain.users.service;

import DGU_AI_LAB.admin_be.domain.users.dto.response.UserTokenResponseDTO;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.UnauthorizedException;
import DGU_AI_LAB.admin_be.global.auth.CustomUserDetailsService;
import DGU_AI_LAB.admin_be.global.auth.jwt.JwtProvider;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class TokenService {

    private final JwtProvider jwtProvider;
    private final RedisTemplate<String, String> redisTemplate;
    private final CustomUserDetailsService customUserDetailsService;

    /**
     * 저장된 리프레시 토큰이 낸 것과 같을 때만 새것으로 바꾼다. 확인과 교체를 따로 하면 같은 토큰으로 동시에 온
     * 두 요청이 모두 통과해, 한쪽이 받은 새 토큰이 곧바로 죽은 토큰이 된다.
     */
    static final RedisScript<Long> REPLACE_IF_MATCHES = new DefaultRedisScript<>(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then "
                    + "redis.call('SET', KEYS[1], ARGV[2], 'PX', ARGV[3]) return 1 "
                    + "end return 0",
            Long.class);

    @Value("${jwt.refresh-token-expire-time}")
    private long REFRESH_TOKEN_EXPIRE_TIME;

    public String issueNewAccessToken(Long userId) {
        return jwtProvider.getIssueToken(userId, true);
    }

    public String issueNewRefreshToken(Long userId) {
        return jwtProvider.getIssueToken(userId, false);
    }

    public UserTokenResponseDTO issueTempToken(Long userId) {
        String accessToken = issueNewAccessToken(userId);
        String refreshToken = issueNewRefreshToken(userId);
        return UserTokenResponseDTO.of(accessToken, refreshToken);
    }

    public UserTokenResponseDTO issueToken(Long userId) {
        String accessToken = issueNewAccessToken(userId);

        String redisKey = "RT:" + userId;
        String storedRefreshToken = redisTemplate.opsForValue().get(redisKey);

        if (storedRefreshToken == null) {
            storedRefreshToken = issueNewRefreshToken(userId);
            redisTemplate.opsForValue().set(redisKey, storedRefreshToken, REFRESH_TOKEN_EXPIRE_TIME, TimeUnit.MILLISECONDS);
        }

        return UserTokenResponseDTO.of(accessToken, storedRefreshToken);
    }

    /**
     * 리프레시 토큰으로 액세스 토큰을 다시 받는다. 리프레시 토큰도 함께 새것으로 바꾸므로(교체 발급), 낸 토큰은
     * 이번 한 번만 통한다 — 빼돌린 토큰을 나중에 다시 내면 저장된 것과 달라 거절된다.
     *
     * @throws UnauthorizedException 토큰이 만료·변조됐거나, 저장된 것과 다르거나(로그아웃·다른 곳에서 로그인·이미 교체됨),
     *                               비밀번호를 바꾸기 전에 발급됐거나, 계정이 비활성화된 경우
     */
    public UserTokenResponseDTO refresh(String refreshToken) {
        Claims claims = jwtProvider.parseRefreshToken(refreshToken);
        Long userId = Long.valueOf(claims.getSubject());

        User user = customUserDetailsService.loadUserEntityById(userId);
        if (user.isTokenIssuedBeforePasswordChange(claims.getIssuedAt().toInstant())) {
            throw new UnauthorizedException(ErrorCode.INVALID_REFRESH_TOKEN_VALUE);
        }

        String newRefreshToken = issueNewRefreshToken(userId);
        Long replaced = redisTemplate.execute(REPLACE_IF_MATCHES, List.of("RT:" + userId),
                refreshToken, newRefreshToken, String.valueOf(REFRESH_TOKEN_EXPIRE_TIME));
        if (replaced == null || replaced != 1L) {
            throw new UnauthorizedException(ErrorCode.NOT_MATCH_REFRESH_TOKEN);
        }
        return UserTokenResponseDTO.of(issueNewAccessToken(userId), newRefreshToken);
    }

    public void logout(Long userId) {
        String redisKey = "RT:" + userId;
        redisTemplate.delete(redisKey);
    }
}
