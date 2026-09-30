package DGU_AI_LAB.admin_be.global.auth.jwt;

import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.UnauthorizedException;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

@Getter
@Component
public class JwtProvider {
    @Value("${jwt.secret}")
    private String secretKey;
    @Value("${jwt.access-token-expire-time}")
    private long ACCESS_TOKEN_EXPIRE_TIME;
    @Value("${jwt.refresh-token-expire-time}")
    private long REFRESH_TOKEN_EXPIRE_TIME;

    private Key signingKey;

    // 액세스·리프레시 토큰은 서명·형식이 같아, 종류를 적어 두지 않으면 7일짜리 리프레시 토큰이 API 인증에 그대로 통한다.
    private static final String TOKEN_TYPE_CLAIM = "token_type";
    private static final String ACCESS = "access";
    private static final String REFRESH = "refresh";

    @PostConstruct
    private void initSigningKey() {
        this.signingKey = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
    }

    public String getIssueToken(Long userId, boolean isAccessToken) {
        if (isAccessToken) return generateToken(userId, ACCESS_TOKEN_EXPIRE_TIME, ACCESS);
        else return generateToken(userId, REFRESH_TOKEN_EXPIRE_TIME, REFRESH);
    }

    /** 서명·만료·종류(access)를 확인하고 클레임을 돌려준다. */
    public Claims parseAccessToken(String accessToken) {
        Claims claims;
        try {
            claims = getJwtParser().parseClaimsJws(accessToken).getBody();
        } catch (ExpiredJwtException e) {
            throw new UnauthorizedException(ErrorCode.EXPIRED_ACCESS_TOKEN);
        } catch (Exception e) {
            throw new UnauthorizedException(ErrorCode.INVALID_ACCESS_TOKEN_VALUE);
        }
        if (!ACCESS.equals(claims.get(TOKEN_TYPE_CLAIM, String.class))) {
            throw new UnauthorizedException(ErrorCode.INVALID_ACCESS_TOKEN_VALUE);
        }
        return claims;
    }

    public void validateAccessToken(String accessToken) {
        parseAccessToken(accessToken);
    }

    public Long getSubject(String token) {
        return Long.valueOf(getJwtParser().parseClaimsJws(token)
                .getBody()
                .getSubject());
    }

    private String generateToken(Long userId, long tokenTime, String tokenType) {
        final Date now = new Date();
        final Date expiration = new Date(now.getTime() + tokenTime);
        return Jwts.builder()
                .setHeaderParam(Header.TYPE, Header.JWT_TYPE)
                .setSubject(String.valueOf(userId))
                .claim(TOKEN_TYPE_CLAIM, tokenType)
                .setIssuedAt(now)
                .setExpiration(expiration)
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    private JwtParser getJwtParser() {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build();
    }

    private Key getSigningKey() {
        return signingKey;
    }
}