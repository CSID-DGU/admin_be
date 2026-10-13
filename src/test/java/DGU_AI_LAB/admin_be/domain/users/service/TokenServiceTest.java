package DGU_AI_LAB.admin_be.domain.users.service;

import DGU_AI_LAB.admin_be.domain.users.dto.response.UserTokenResponseDTO;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.UnauthorizedException;
import DGU_AI_LAB.admin_be.global.auth.CustomUserDetailsService;
import DGU_AI_LAB.admin_be.global.auth.jwt.JwtProvider;
import io.jsonwebtoken.Claims;
import java.util.Date;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
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
    private CustomUserDetailsService customUserDetailsService;

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

    @Nested
    @DisplayName("refresh")
    class Refresh {

        private final Date issuedAt = new Date();

        private User stubValidToken(String token, boolean issuedBeforePasswordChange) {
            Claims claims = mock(Claims.class);
            when(claims.getSubject()).thenReturn("1");
            when(claims.getIssuedAt()).thenReturn(issuedAt);
            when(jwtProvider.parseRefreshToken(token)).thenReturn(claims);
            User user = mock(User.class);
            when(user.isTokenIssuedBeforePasswordChange(issuedAt.toInstant())).thenReturn(issuedBeforePasswordChange);
            when(customUserDetailsService.loadUserEntityById(1L)).thenReturn(user);
            return user;
        }

        @Test
        @DisplayName("저장된 것과 같은 리프레시 토큰이면 액세스·리프레시 토큰을 모두 새로 준다")
        void rotatesBothTokens() {
            ReflectionTestUtils.setField(tokenService, "REFRESH_TOKEN_EXPIRE_TIME", 604800000L);
            stubValidToken("oldRefresh", false);
            when(jwtProvider.getIssueToken(1L, false)).thenReturn("newRefresh");
            when(jwtProvider.getIssueToken(1L, true)).thenReturn("newAccess");
            when(redisTemplate.execute(TokenService.REPLACE_IF_MATCHES, List.of("RT:1"),
                    "oldRefresh", "newRefresh", "604800000")).thenReturn(1L);

            UserTokenResponseDTO result = tokenService.refresh("oldRefresh");

            assertThat(result.accessToken()).isEqualTo("newAccess");
            assertThat(result.refreshToken()).isEqualTo("newRefresh");
        }

        @Test
        @DisplayName("저장된 것과 다르면(이미 교체됐거나 로그아웃됨) 거절하고 액세스 토큰을 주지 않는다")
        void rejectsWhenStoredTokenDiffers() {
            stubValidToken("oldRefresh", false);
            when(jwtProvider.getIssueToken(1L, false)).thenReturn("newRefresh");
            when(redisTemplate.execute(eq(TokenService.REPLACE_IF_MATCHES), anyList(), any(Object[].class))).thenReturn(0L);

            assertThatThrownBy(() -> tokenService.refresh("oldRefresh"))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_MATCH_REFRESH_TOKEN);
            verify(jwtProvider, never()).getIssueToken(1L, true);
        }

        @Test
        @DisplayName("교체 스크립트는 낸 토큰이 저장된 것과 다르면 저장된 토큰을 지운다")
        void scriptRevokesStoredTokenOnMismatch() {
            String script = TokenService.REPLACE_IF_MATCHES.getScriptAsString();

            assertThat(script).contains("redis.call('DEL', KEYS[1]) return 0");
        }

        @Test
        @DisplayName("비밀번호를 바꾸기 전에 발급된 토큰이면 거절하고 저장된 토큰을 건드리지 않는다")
        void rejectsTokenIssuedBeforePasswordChange() {
            stubValidToken("oldRefresh", true);

            assertThatThrownBy(() -> tokenService.refresh("oldRefresh"))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_REFRESH_TOKEN_VALUE);
            verifyNoInteractions(redisTemplate);
        }

        @Test
        @DisplayName("비활성화된 계정이면 거절한다")
        void rejectsDisabledAccount() {
            Claims claims = mock(Claims.class);
            when(claims.getSubject()).thenReturn("1");
            when(jwtProvider.parseRefreshToken("oldRefresh")).thenReturn(claims);
            when(customUserDetailsService.loadUserEntityById(1L))
                    .thenThrow(new UnauthorizedException(ErrorCode.ACCOUNT_DISABLED));

            assertThatThrownBy(() -> tokenService.refresh("oldRefresh"))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCOUNT_DISABLED);
            verifyNoInteractions(redisTemplate);
        }

        @Test
        @DisplayName("만료·변조된 토큰이면 사용자를 읽지 않고 거절한다")
        void rejectsInvalidToken() {
            when(jwtProvider.parseRefreshToken("bad"))
                    .thenThrow(new UnauthorizedException(ErrorCode.EXPIRED_REFRESH_TOKEN));

            assertThatThrownBy(() -> tokenService.refresh("bad"))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EXPIRED_REFRESH_TOKEN);
            verifyNoInteractions(customUserDetailsService, redisTemplate);
        }
    }
}
