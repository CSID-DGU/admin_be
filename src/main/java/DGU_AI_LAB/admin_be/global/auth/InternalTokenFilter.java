package DGU_AI_LAB.admin_be.global.auth;

import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.dto.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * config-server·인프라 전용 API는 사용자 JWT 대신 공유 토큰(X-Internal-Token)으로만 연다.
 * admin_be가 config-server를 부를 때 쓰는 config.api-token과 같은 값이다. 이 경로들은 사용자
 * 신청의 우분투 비밀번호 같은 값을 돌려주므로, 토큰이 설정돼 있지 않으면 전부 거절한다(fail-closed).
 */
@Slf4j
public class InternalTokenFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Internal-Token";

    static final List<String> PROTECTED_PATHS = List.of("/api/requests/config/**", "/api/internal/**");
    // 신청 화면에서 로그인한 사용자가 부르는 이름 중복 확인은 비밀 정보를 돌려주지 않아 그대로 둔다.
    static final String EXEMPT_PATH = "/api/requests/config/check-username";

    private static final AntPathMatcher pathMatcher = new AntPathMatcher();

    private final byte[] expected;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public InternalTokenFilter(String apiToken) {
        this.expected = apiToken == null ? new byte[0] : apiToken.getBytes(StandardCharsets.UTF_8);
        if (expected.length == 0) {
            log.warn("[InternalTokenFilter] config.api-token 미설정 — 내부 전용 API를 모두 거절합니다.");
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (EXEMPT_PATH.equals(path)) {
            return true;
        }
        return PROTECTED_PATHS.stream().noneMatch(pattern -> pathMatcher.match(pattern, path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!isValid(request.getHeader(HEADER))) {
            log.warn("[InternalTokenFilter] 내부 토큰 불일치로 거절: uri={}, remote={}",
                    request.getRequestURI(), request.getRemoteAddr());
            response.setStatus(ErrorCode.UNAUTHORIZED.getHttpStatus().value());
            response.setContentType("application/json;charset=UTF-8");
            objectMapper.writeValue(response.getWriter(), ErrorResponse.of(ErrorCode.UNAUTHORIZED));
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean isValid(String sent) {
        if (expected.length == 0 || sent == null) {
            return false;
        }
        return MessageDigest.isEqual(sent.getBytes(StandardCharsets.UTF_8), expected);
    }
}
