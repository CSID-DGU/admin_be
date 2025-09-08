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
            "/api/requests/config/**" // 개발 완료 이후 IP 제한 필요
            //"/api/groups/**"
    );
}
