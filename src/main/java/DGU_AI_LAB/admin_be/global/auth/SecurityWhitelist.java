package DGU_AI_LAB.admin_be.global.auth;

import java.util.List;

public class SecurityWhitelist {

    // 인증이 필요 없는 모든 경로는 여기에 정의합니다
    public static final List<String> UNPROTECTED_PATHS = List.of(
            "/", "/swagger-ui/**", "/swagger/**", "/v3/api-docs/**",
            "/api/auth/login", "/api/auth/register",
            "/api/auth/token-refresh", // 액세스 토큰이 만료된 뒤에 부르므로 리프레시 토큰으로만 확인한다
            "/api/auth/email/**",
            "/api/auth/password-reset-codes", "/api/auth/password-resets", // 로그인 못 하는 사용자가 메일 인증으로 신청
            "/actuator/health", "/actuator/info",
            "/api/requests/config/**", // JWT 대신 InternalTokenFilter(X-Internal-Token)로 보호
            "/api/internal/**"         // JWT 대신 InternalTokenFilter(X-Internal-Token)로 보호
    );

    // 신청 화면의 이름 중복 확인. 위의 /api/requests/config/** 범위 안에 있지만 내부 토큰이 아니라 로그인(JWT)으로
    // 확인한다 — 로그인 없이 열어 두면 누구나 어떤 계정 이름이 쓰이고 있는지 횟수 제한 없이 알아낼 수 있다.
    public static final String USERNAME_CHECK_PATH = "/api/requests/config/check-username";

    /** JWT 없이 지나가는 경로인지. 판정은 라우팅과 같은 풀린(퍼센트 디코딩된) 경로로 한다. */
    public static boolean isUnprotected(String path, org.springframework.util.PathMatcher matcher) {
        if (USERNAME_CHECK_PATH.equals(path)) {
            return false;
        }
        return UNPROTECTED_PATHS.stream().anyMatch(pattern -> matcher.match(pattern, path));
    }
}
