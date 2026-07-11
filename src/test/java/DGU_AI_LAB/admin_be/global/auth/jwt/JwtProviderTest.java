package DGU_AI_LAB.admin_be.global.auth.jwt;

import DGU_AI_LAB.admin_be.error.exception.UnauthorizedException;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.Key;
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
    }

    // ─── 헬퍼: 이미 만료된 토큰 직접 생성 ───────────────────────────────────────
    private String buildExpiredToken(Long userId) {
        String encoded = Base64.getEncoder().encodeToString(SECRET.getBytes());
        Key key = Keys.hmacShaKeyFor(encoded.getBytes());
        Date past = new Date(System.currentTimeMillis() - 10_000);
        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .setIssuedAt(past)
                .setExpiration(past)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    // ─── 헬퍼: 완전히 다른 키로 서명된 위조 토큰 생성 ────────────────────────────
    private String buildTokenWithDifferentKey(Long userId) {
        String fakeSecret = "completely-different-secret-key-abcdefghijklmnop-xyz";
        String encoded = Base64.getEncoder().encodeToString(fakeSecret.getBytes());
        Key key = Keys.hmacShaKeyFor(encoded.getBytes());
        Date now = new Date();
        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + ACCESS_TTL))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    // ───────────────────────────────────────────────────────────────
    // getSubjectFromExpiredToken
    // ───────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("getSubjectFromExpiredToken")
    class GetSubjectFromExpiredToken {

        @Test
        @DisplayName("유효한 토큰이면 서명 검증 후 userId를 반환한다")
        void returnsUserId_fromValidToken() {
            String token = jwtProvider.getIssueToken(42L, true);

            Long userId = jwtProvider.getSubjectFromExpiredToken(token);

            assertThat(userId).isEqualTo(42L);
        }

        @Test
        @DisplayName("만료된 토큰이어도 서명이 유효하면 userId를 반환한다")
        void returnsUserId_fromExpiredButValidToken() {
            String expiredToken = buildExpiredToken(99L);

            Long userId = jwtProvider.getSubjectFromExpiredToken(expiredToken);

            assertThat(userId).isEqualTo(99L);
        }

        @Test
        @DisplayName("서명이 다른 위조 토큰이면 예외를 던진다")
        void throwsException_whenSignatureIsInvalid() {
            String forgedToken = buildTokenWithDifferentKey(1L);

            assertThatThrownBy(() -> jwtProvider.getSubjectFromExpiredToken(forgedToken))
                    .isNotInstanceOf(ExpiredJwtException.class);
        }

        @Test
        @DisplayName("형식이 잘못된 토큰이면 예외를 던진다")
        void throwsException_whenTokenIsMalformed() {
            assertThatThrownBy(() -> jwtProvider.getSubjectFromExpiredToken("not.a.valid.token"))
                    .isNotInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("점(.)이 없는 토큰이면 예외를 던진다")
        void throwsException_whenTokenHasNoParts() {
            assertThatThrownBy(() -> jwtProvider.getSubjectFromExpiredToken("invalidtoken"))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("빈 문자열 토큰이면 예외를 던진다")
        void throwsException_whenTokenIsEmpty() {
            assertThatThrownBy(() -> jwtProvider.getSubjectFromExpiredToken(""))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("payload의 sub만 바꾼 위조 토큰은 서명 불일치로 예외를 던진다")
        void throwsException_whenPayloadIsManipulated() {
            String realToken = jwtProvider.getIssueToken(1L, true);
            String[] parts = realToken.split("\\.");
            // payload의 sub를 999로 변조 (재인코딩, 서명은 그대로)
            String fakePayload = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString("{\"sub\":\"999\",\"iat\":0,\"exp\":9999999999}".getBytes());
            String forgedToken = parts[0] + "." + fakePayload + "." + parts[2];

            assertThatThrownBy(() -> jwtProvider.getSubjectFromExpiredToken(forgedToken))
                    .isInstanceOf(Exception.class);
        }
    }

    // ───────────────────────────────────────────────────────────────
    // validateAccessToken
    // ───────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("validateAccessToken")
    class ValidateAccessToken {

        @Test
        @DisplayName("유효한 액세스 토큰은 예외를 던지지 않는다")
        void doesNotThrow_whenTokenIsValid() {
            String token = jwtProvider.getIssueToken(1L, true);

            assertThatCode(() -> jwtProvider.validateAccessToken(token))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("만료된 액세스 토큰은 UnauthorizedException을 던진다")
        void throwsUnauthorized_whenTokenIsExpired() {
            String expiredToken = buildExpiredToken(1L);

            assertThatThrownBy(() -> jwtProvider.validateAccessToken(expiredToken))
                    .isInstanceOf(UnauthorizedException.class);
        }

        @Test
        @DisplayName("서명이 잘못된 토큰은 UnauthorizedException을 던진다")
        void throwsUnauthorized_whenSignatureIsInvalid() {
            String forgedToken = buildTokenWithDifferentKey(1L);

            assertThatThrownBy(() -> jwtProvider.validateAccessToken(forgedToken))
                    .isInstanceOf(UnauthorizedException.class);
        }

        @Test
        @DisplayName("형식이 잘못된 토큰은 UnauthorizedException을 던진다")
        void throwsUnauthorized_whenTokenIsMalformed() {
            assertThatThrownBy(() -> jwtProvider.validateAccessToken("bad.token"))
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
    }
}
