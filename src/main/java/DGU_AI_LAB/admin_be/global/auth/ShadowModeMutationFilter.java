package DGU_AI_LAB.admin_be.global.auth;

import DGU_AI_LAB.admin_be.global.config.RuntimeModeProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * SHADOW 인스턴스의 중앙 fail-closed HTTP gate. 인증 여부나 컨트롤러 구현과 관계없이 쓰기 메서드는
 * security chain의 앞에서 거절한다. 따라서 신규 mutation endpoint가 생겨도 별도 allowlist 누락으로
 * shadow가 운영 데이터를 바꾸지 않는다.
 */
final class ShadowModeMutationFilter extends OncePerRequestFilter {

    static final String ERROR_CODE = "RUNTIME_SHADOW_READ_ONLY";
    private static final Set<String> SAFE_METHODS = Set.of(
            HttpMethod.GET.name(), HttpMethod.HEAD.name(), HttpMethod.OPTIONS.name()
    );

    private final RuntimeModeProperties runtimeMode;

    ShadowModeMutationFilter(RuntimeModeProperties runtimeMode) {
        this.runtimeMode = runtimeMode;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !runtimeMode.shadow() || SAFE_METHODS.contains(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setHeader(HttpHeaders.RETRY_AFTER, "60");
        response.getWriter().write("{\"status\":503,\"message\":\"Shadow instance is read-only.\",\"code\":\""
                + ERROR_CODE + "\"}");
    }
}
