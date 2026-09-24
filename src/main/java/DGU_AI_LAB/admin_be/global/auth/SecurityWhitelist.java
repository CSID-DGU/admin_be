package DGU_AI_LAB.admin_be.global.auth;

import java.util.List;

public class SecurityWhitelist {

    // 인증이 필요 없는 모든 경로는 여기에 정의합니다
    public static final List<String> UNPROTECTED_PATHS = List.of(
            "/", "/swagger-ui/**", "/swagger/**", "/v3/api-docs/**",
            "/api/auth/token/**", "/api/auth/users/password/**", "/api/auth/login", "/api/auth/register", "/api/auth/reissue",
            "/api/auth/email/**",
            "/auth/callback/**",
            "/actuator/health", "/actuator/info",
            "/api/requests/config/**", // JWT 대신 InternalTokenFilter(X-Internal-Token)로 보호
            "/api/internal/**",        // JWT 대신 InternalTokenFilter(X-Internal-Token)로 보호
            "/api/monitoring/**"       // 공개 모니터링 지표 (인증 불필요)
            //"/api/groups/**"
    );
}
