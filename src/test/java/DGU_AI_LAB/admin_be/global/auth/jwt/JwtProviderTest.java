package DGU_AI_LAB.admin_be.global.auth.jwt;

import DGU_AI_LAB.admin_be.error.exception.UnauthorizedException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JwtProvider")
class JwtProviderTest {

    private JwtProvider jwtProvider;

    private static final String SECRET = "dguailab-test-secret-key-1234567890-1234567890-1234567890";
    private static final long ACCESS_TTL  = 3_600_000L;  // 1시간
    private static final long REFRESH_TTL = 604_800_000L; // 7일

    @BeforeEach
    void setUp() {
        jwtProvider = new JwtProvider();
        ReflectionTestUtils.setField(jwtProvider, "secretKey", SECRET);
        ReflectionTestUtils.setField(jwtProvider, "ACCESS_TOKEN_EXPIRE_TIME", ACCESS_TTL);
        ReflectionTestUtils.setField(jwtProvider, "REFRESH_TOKEN_EXPIRE_TIME", REFRESH_TTL);
        ReflectionTestUtils.invokeMethod(jwtProvider, "initSigningKey");
    }

    // ─── 헬퍼: 이미 만료된 토큰 직접 생성 ───────────────────────────────────────
    private String buildExpiredToken(Long userId) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes());
        Date past = new Date(System.currentTimeMillis() - 10_000);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(past)
                .expiration(past)
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    // ─── 헬퍼: 완전히 다른 키로 서명된 위조 토큰 생성 ────────────────────────────
    private String buildTokenWithDifferentKey(Long userId) {
        String fakeSecret = "completely-different-secret-key-abcdefghijklmnop-xyz";
        SecretKey key = Keys.hmacShaKeyFor(fakeSecret.getBytes());
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ACCESS_TTL))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    // ───────────────────────────────────────────────────────────────
    // parseAccessToken
    // ───────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("parseAccessToken")
    class ValidateAccessToken {

        @Test
        @DisplayName("유효한 액세스 토큰은 예외를 던지지 않는다")
        void doesNotThrow_whenTokenIsValid() {
            String token = jwtProvider.getIssueToken(1L, true);

            assertThatCode(() -> jwtProvider.parseAccessToken(token))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("만료된 액세스 토큰은 UnauthorizedException을 던진다")
        void throwsUnauthorized_whenTokenIsExpired() {
            String expiredToken = buildExpiredToken(1L);

            assertThatThrownBy(() -> jwtProvider.parseAccessToken(expiredToken))
                    .isInstanceOf(UnauthorizedException.class);
        }

        @Test
        @DisplayName("서명이 잘못된 토큰은 UnauthorizedException을 던진다")
        void throwsUnauthorized_whenSignatureIsInvalid() {
            String forgedToken = buildTokenWithDifferentKey(1L);

            assertThatThrownBy(() -> jwtProvider.parseAccessToken(forgedToken))
                    .isInstanceOf(UnauthorizedException.class);
        }

        @Test
        @DisplayName("형식이 잘못된 토큰은 UnauthorizedException을 던진다")
        void throwsUnauthorized_whenTokenIsMalformed() {
            assertThatThrownBy(() -> jwtProvider.parseAccessToken("bad.token"))
                    .isInstanceOf(UnauthorizedException.class);
        }

        @Test
        @DisplayName("리프레시 토큰은 액세스 토큰으로 쓸 수 없다")
        void throwsUnauthorized_whenRefreshTokenIsUsedAsAccessToken() {
            String refreshToken = jwtProvider.getIssueToken(1L, false);

            assertThatThrownBy(() -> jwtProvider.parseAccessToken(refreshToken))
                    .isInstanceOf(UnauthorizedException.class);
        }

        @Test
        @DisplayName("종류가 적히지 않은(이전 방식) 토큰은 액세스 토큰으로 받지 않는다")
        void throwsUnauthorized_whenTokenTypeIsMissing() {
            SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes());
            Date now = new Date();
            String untyped = Jwts.builder()
                    .subject("1")
                    .issuedAt(now)
                    .expiration(new Date(now.getTime() + ACCESS_TTL))
                    .signWith(key, Jwts.SIG.HS256)
                    .compact();

            assertThatThrownBy(() -> jwtProvider.parseAccessToken(untyped))
                    .isInstanceOf(UnauthorizedException.class);
        }
    }

    // ───────────────────────────────────────────────────────────────
    // getIssueToken / getSubject
    // ───────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("getIssueToken")
    class GetIssueToken {

        @Test
        @DisplayName("액세스 토큰 발급 후 subject로 userId를 꺼낼 수 있다")
        void accessToken_containsCorrectUserId() {
            String token = jwtProvider.getIssueToken(7L, true);

            Long subject = jwtProvider.getSubject(token);

            assertThat(subject).isEqualTo(7L);
        }

        @Test
        @DisplayName("리프레시 토큰 발급 후 subject로 userId를 꺼낼 수 있다")
        void refreshToken_containsCorrectUserId() {
            String token = jwtProvider.getIssueToken(7L, false);

            Long subject = jwtProvider.getSubject(token);

            assertThat(subject).isEqualTo(7L);
        }

        @Test
        @DisplayName("헤더는 HS256·JWT로 나간다 — 이미 발급된 토큰과 형식이 같아야 한다")
        void header_isHs256Jwt() {
            String token = jwtProvider.getIssueToken(7L, true);

            String header = new String(Base64.getUrlDecoder().decode(token.split("\\.")[0]), StandardCharsets.UTF_8);

            assertThat(header).contains("\"alg\":\"HS256\"").contains("\"typ\":\"JWT\"");
        }

        @Test
        @DisplayName("비밀값이 64바이트 이상이어도 HS256으로 발급하고 검증한다")
        void longSecret_stillUsesHs256() {
            ReflectionTestUtils.setField(jwtProvider, "secretKey", SECRET.repeat(2));
            ReflectionTestUtils.invokeMethod(jwtProvider, "initSigningKey");

            String token = jwtProvider.getIssueToken(7L, true);

            String header = new String(Base64.getUrlDecoder().decode(token.split("\\.")[0]), StandardCharsets.UTF_8);
            assertThat(header).contains("\"alg\":\"HS256\"");
            assertThat(jwtProvider.getSubject(token)).isEqualTo(7L);
        }
    }

    @Nested
    @DisplayName("parseRefreshToken")
    class ParseRefreshToken {

        @Test
        @DisplayName("유효한 리프레시 토큰이면 사용자 번호가 담긴 클레임을 돌려준다")
        void returnsClaims_whenTokenIsValid() {
            String token = jwtProvider.getIssueToken(7L, false);

            assertThat(jwtProvider.parseRefreshToken(token).getSubject()).isEqualTo("7");
        }

        @Test
        @DisplayName("액세스 토큰을 내면 거절한다")
        void rejectsAccessToken() {
            String token = jwtProvider.getIssueToken(7L, true);

            assertThatThrownBy(() -> jwtProvider.parseRefreshToken(token))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasFieldOrPropertyWithValue("errorCode", DGU_AI_LAB.admin_be.error.ErrorCode.INVALID_REFRESH_TOKEN_VALUE);
        }

        @Test
        @DisplayName("만료된 토큰이면 만료로 거절한다")
        void rejectsExpiredToken() {
            assertThatThrownBy(() -> jwtProvider.parseRefreshToken(buildExpiredToken(7L)))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasFieldOrPropertyWithValue("errorCode", DGU_AI_LAB.admin_be.error.ErrorCode.EXPIRED_REFRESH_TOKEN);
        }

        @Test
        @DisplayName("다른 키로 서명된 토큰이면 거절한다")
        void rejectsForgedToken() {
            assertThatThrownBy(() -> jwtProvider.parseRefreshToken(buildTokenWithDifferentKey(7L)))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasFieldOrPropertyWithValue("errorCode", DGU_AI_LAB.admin_be.error.ErrorCode.INVALID_REFRESH_TOKEN_VALUE);
        }
    }
}
