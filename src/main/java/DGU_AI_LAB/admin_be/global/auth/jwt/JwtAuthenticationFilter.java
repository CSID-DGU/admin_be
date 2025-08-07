package DGU_AI_LAB.admin_be.global.auth.jwt;

import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.UnauthorizedException;
import DGU_AI_LAB.admin_be.global.auth.CustomUserDetails;
import DGU_AI_LAB.admin_be.global.auth.CustomUserDetailsService;
import DGU_AI_LAB.admin_be.global.auth.SecurityWhitelist;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION = "Authorization";
    private static final String BEARER = "Bearer ";

    private final JwtProvider jwtProvider;
    private final CustomUserDetailsService customUserDetailsService;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            log.info("[JwtAuthFilter] 요청 URI: {}", request.getRequestURI());

            final String accessToken = getAccessTokenFromHttpServletRequest(request);
            log.info("[JwtAuthFilter] 추출된 AccessToken: {}", accessToken);

            jwtProvider.validateAccessToken(accessToken);
            log.info("[JwtAuthFilter] AccessToken 유효성 검사 통과");

            final Long userId = jwtProvider.getSubject(accessToken);
            User user = customUserDetailsService.loadUserEntityById(userId);

            CustomUserDetails userDetails = new CustomUserDetails(user, null);
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);

            log.info("[JwtAuthFilter] SecurityContextHolder 인증 객체 설정 완료");

        } catch (UnauthorizedException e) {
            log.warn("[JwtAuthFilter] 인증 실패 - {}", e.getMessage());
            setErrorResponse(response, e.getErrorCode());
            return;
        } catch (Exception e) {
            log.error("[JwtAuthFilter] 알 수 없는 예외 발생", e);
            setErrorResponse(response, ErrorCode.AUTHENTICATION_FAILED);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String getAccessTokenFromHttpServletRequest(HttpServletRequest request) {
        String accessToken = request.getHeader(AUTHORIZATION);
        log.info("[JwtAuthFilter] Authorization 헤더: {}", accessToken);
        if (StringUtils.hasText(accessToken) && accessToken.startsWith(BEARER)) {
            return accessToken.substring(BEARER.length());
        }
        throw new UnauthorizedException(ErrorCode.INVALID_ACCESS_TOKEN);
    }

    private void setErrorResponse(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getHttpStatus().value());
        response.setContentType("application/json;charset=UTF-8");

        Map<String, Object> errorBody = new HashMap<>();
        errorBody.put("status", errorCode.getHttpStatus().value());
        errorBody.put("message", errorCode.getMessage());

        objectMapper.writeValue(response.getWriter(), errorBody);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        log.info("[JwtAuthFilter] 현재 요청 URI = {}", path);

        for (String pattern : SecurityWhitelist.UNPROTECTED_PATHS) {
            if (pathMatcher.match(pattern, path)) {
                log.info("[JwtAuthFilter] 인증 불필요한 경로: {}", pattern);
                return true;
            }
        }
        return false;
    }

}
