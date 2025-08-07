package DGU_AI_LAB.admin_be.global.auth;

import java.util.List;

public class SecurityWhitelist {

    // 인증이 필요 없는 모든 경로는 여기에 정의합니다
    public static final List<String> UNPROTECTED_PATHS = List.of(
            "/", "/swagger-ui/**", "/swagger/**", "/v3/api-docs/**",
            "/api/auth/token/**",
            "/api/auth/login", "/api/auth/register", "/api/auth/reissue",
            "/api/auth/email/**",
            "/auth/callback/**",
            "/actuator/health", "/actuator/info"
    );
}
