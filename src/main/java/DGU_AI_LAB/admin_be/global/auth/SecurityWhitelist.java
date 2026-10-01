package DGU_AI_LAB.admin_be.global.auth;

import java.util.List;

public class SecurityWhitelist {

    // 인증이 필요 없는 모든 경로는 여기에 정의합니다
    public static final List<String> UNPROTECTED_PATHS = List.of(
            "/", "/swagger-ui/**", "/swagger/**", "/v3/api-docs/**",
            "/api/auth/login", "/api/auth/register",
            "/api/auth/email/**",
            "/api/auth/password-resets", "/api/auth/password", // 로그인 못 하는 사용자가 메일 인증 코드로 재설정
            "/actuator/health", "/actuator/info",
            "/api/requests/config/**", // JWT 대신 InternalTokenFilter(X-Internal-Token)로 보호
            "/api/internal/**"         // JWT 대신 InternalTokenFilter(X-Internal-Token)로 보호
    );
}
