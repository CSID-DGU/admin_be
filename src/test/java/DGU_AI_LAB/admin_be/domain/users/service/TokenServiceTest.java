package DGU_AI_LAB.admin_be.domain.users.service;

import DGU_AI_LAB.admin_be.domain.users.dto.request.UserTokenRequestDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.response.UserTokenResponseDTO;
import DGU_AI_LAB.admin_be.error.exception.UnauthorizedException;
import DGU_AI_LAB.admin_be.global.auth.jwt.JwtProvider;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

    @InjectMocks
    private TokenService tokenService;

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Nested
    @DisplayName("issueToken")
    class IssueToken {

        @Test
        @DisplayName("Redis에 리프레시 토큰이 없으면 새로 발급하고 저장한다")
        void issueToken_createsNewRefreshToken_whenNotInRedis() {
            ReflectionTestUtils.setField(tokenService, "REFRESH_TOKEN_EXPIRE_TIME", 604800L);
            when(jwtProvider.getIssueToken(1L, true)).thenReturn("newAccessToken");
            when(jwtProvider.getIssueToken(1L, false)).thenReturn("newRefreshToken");
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("RT:1")).thenReturn(null);

            UserTokenResponseDTO result = tokenService.issueToken(1L);

            assertThat(result.accessToken()).isEqualTo("newAccessToken");
            assertThat(result.refreshToken()).isEqualTo("newRefreshToken");
            verify(valueOperations).set(eq("RT:1"), eq("newRefreshToken"), anyLong(), any());
        }

        @Test
        @DisplayName("Redis에 기존 리프레시 토큰이 있으면 재사용한다")
        void issueToken_reusesExistingRefreshToken_whenInRedis() {
            when(jwtProvider.getIssueToken(1L, true)).thenReturn("newAccessToken");
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("RT:1")).thenReturn("existingRefreshToken");

            UserTokenResponseDTO result = tokenService.issueToken(1L);

            assertThat(result.accessToken()).isEqualTo("newAccessToken");
            assertThat(result.refreshToken()).isEqualTo("existingRefreshToken");
            verify(jwtProvider, never()).getIssueToken(1L, false);
        }
    }

    @Nested
    @DisplayName("issueTempToken")
    class IssueTempToken {

        @Test
        @DisplayName("임시 토큰을 발급하면 액세스/리프레시 토큰을 모두 새로 발급한다")
        void issueTempToken_issuesBothTokens() {
            when(jwtProvider.getIssueToken(1L, true)).thenReturn("tempAccess");
            when(jwtProvider.getIssueToken(1L, false)).thenReturn("tempRefresh");

            UserTokenResponseDTO result = tokenService.issueTempToken(1L);

            assertThat(result.accessToken()).isEqualTo("tempAccess");
            assertThat(result.refreshToken()).isEqualTo("tempRefresh");
        }
    }

    @Nested
    @DisplayName("reissue")
    class Reissue {

        @Test
        @DisplayName("유효한 리프레시 토큰으로 재발급하면 새 토큰을 반환한다")
        void reissue_success() throws JsonProcessingException {
            ReflectionTestUtils.setField(tokenService, "REFRESH_TOKEN_EXPIRE_TIME", 604800L);
            when(jwtProvider.decodeJwtPayloadSubject("accessToken")).thenReturn("1");
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("RT:1")).thenReturn("storedRefreshToken");
            doNothing().when(jwtProvider).validateRefreshToken("refreshToken");
            doNothing().when(jwtProvider).equalsRefreshToken("refreshToken", "storedRefreshToken");
            when(jwtProvider.getIssueToken(1L, true)).thenReturn("newAccessToken");
            when(jwtProvider.getIssueToken(1L, false)).thenReturn("newRefreshToken");

            UserTokenRequestDTO dto = new UserTokenRequestDTO("accessToken", "refreshToken");
            UserTokenResponseDTO result = tokenService.reissue(dto);

            assertThat(result.accessToken()).isEqualTo("newAccessToken");
            assertThat(result.refreshToken()).isEqualTo("newRefreshToken");
        }
    }

    @Nested
    @DisplayName("logout")
    class Logout {

        @Test
        @DisplayName("로그아웃하면 Redis에서 리프레시 토큰을 삭제한다")
        void logout_deletesRefreshTokenFromRedis() {
            tokenService.logout(1L);

            verify(redisTemplate, times(1)).delete("RT:1");
        }
    }
}
