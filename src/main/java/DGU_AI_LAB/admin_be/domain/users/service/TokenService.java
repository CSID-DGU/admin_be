package DGU_AI_LAB.admin_be.domain.users.service;

import DGU_AI_LAB.admin_be.domain.users.dto.request.UserTokenRequestDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.response.UserTokenResponseDTO;
import DGU_AI_LAB.admin_be.error.exception.UnauthorizedException;
import DGU_AI_LAB.admin_be.global.auth.jwt.JwtProvider;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

import static DGU_AI_LAB.admin_be.error.ErrorCode.JSON_PARSING_FAILED;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class TokenService {

    private final JwtProvider jwtProvider;
    private final RedisTemplate<String, String> redisTemplate;

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
            redisTemplate.opsForValue().set(redisKey, storedRefreshToken, REFRESH_TOKEN_EXPIRE_TIME, TimeUnit.SECONDS);
        }

        return UserTokenResponseDTO.of(accessToken, storedRefreshToken);
    }

    public UserTokenResponseDTO reissue(UserTokenRequestDTO userTokenRequest) {
        Long userId;
        try {
            userId = Long.valueOf(jwtProvider.decodeJwtPayloadSubject(userTokenRequest.accessToken()));
        } catch (JsonProcessingException e) {
            throw new UnauthorizedException(JSON_PARSING_FAILED);
        }

        String refreshToken = userTokenRequest.refreshToken();
        String redisKey = "RT:" + userId;

        jwtProvider.validateRefreshToken(refreshToken);

        String storedRefreshToken = redisTemplate.opsForValue().get(redisKey);
        jwtProvider.equalsRefreshToken(refreshToken, storedRefreshToken);

        String newAccessToken = issueNewAccessToken(userId);
        String newRefreshToken = issueNewRefreshToken(userId);

        redisTemplate.opsForValue().set(redisKey, newRefreshToken, REFRESH_TOKEN_EXPIRE_TIME, TimeUnit.SECONDS);

        return UserTokenResponseDTO.of(newAccessToken, newRefreshToken);
    }

    public void logout(Long userId) {
        String redisKey = "RT:" + userId;
        redisTemplate.delete(redisKey);
    }
}
