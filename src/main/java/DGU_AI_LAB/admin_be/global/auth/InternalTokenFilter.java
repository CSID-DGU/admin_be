package DGU_AI_LAB.admin_be.global.auth;

import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.dto.ErrorResponse;
import DGU_AI_LAB.admin_be.global.cluster.ClusterClientProperties;
import DGU_AI_LAB.admin_be.global.cluster.ClusterClientRegistry;
import DGU_AI_LAB.admin_be.global.cluster.ClusterId;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * config-server·인프라 전용 API는 사용자 JWT 대신 클러스터별 X-Internal-Token과
 * X-Cluster-Id를 함께 검증한다. 토큰 하나만 맞아도 다른 클러스터의 신청을 읽을 수 있는
 * 구조가 되지 않도록 토큰과 클러스터 설정을 한 묶음으로 비교한다.
 */
@Slf4j
public class InternalTokenFilter extends OncePerRequestFilter {

    public static final String TOKEN_HEADER = ClusterClientRegistry.API_TOKEN_HEADER;
    public static final String CLUSTER_HEADER = ClusterClientRegistry.CLUSTER_ID_HEADER;
    public static final String VERIFIED_CLUSTER_ATTRIBUTE =
            "DGU_AI_LAB.admin_be.global.auth.InternalTokenFilter.verifiedClusterId";

    static final List<String> PROTECTED_PATHS = List.of("/api/requests/config/**", "/api/internal/**");
    // 신청 화면에서 로그인한 사용자가 부르는 이름 중복 확인은 내부 토큰이 아니라 JWT로 확인한다.
    static final String EXEMPT_PATH = SecurityWhitelist.USERNAME_CHECK_PATH;

    private static final AntPathMatcher pathMatcher = new AntPathMatcher();

    private final Map<String, byte[]> expectedByCluster;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public InternalTokenFilter(ClusterClientProperties properties) {
        Map<String, byte[]> configured = new LinkedHashMap<>();
        Set<String> configuredTokens = new HashSet<>();
        Map<String, ClusterClientProperties.Cluster> enabledClusters =
                properties == null ? null : properties.enabledClusters();
        if (enabledClusters != null) {
            enabledClusters.forEach((rawClusterId, cluster) -> {
                String clusterId = ClusterId.canonical(rawClusterId);
                if (cluster == null || cluster.apiToken() == null || cluster.apiToken().isBlank()) {
                    throw new IllegalStateException(
                            "provi.clusters." + clusterId + ".api-token 설정이 비어 있습니다.");
                }
                if (!configuredTokens.add(cluster.apiToken())) {
                    throw new IllegalStateException(
                            "클러스터별 api-token은 서로 다른 값이어야 합니다: " + clusterId);
                }
                if (configured.putIfAbsent(
                        clusterId,
                        cluster.apiToken().getBytes(StandardCharsets.UTF_8)
                ) != null) {
                    throw new IllegalStateException(
                            "provi.clusters에 대소문자만 다른 clusterId가 겹칩니다: " + rawClusterId);
                }
            });
        }
        this.expectedByCluster = Map.copyOf(configured);
        if (expectedByCluster.isEmpty()) {
            log.warn("[InternalTokenFilter] provi.clusters 미설정 — 내부 전용 API를 모두 거절합니다.");
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // 원본 URI(getRequestURI)는 퍼센트 인코딩이 풀리지 않은 값이라 /api/requests/%63onfig/...처럼 글자를 인코딩하면
        // 이 검사를 비껴가는데, 라우팅은 풀린 경로로 되어 컨트롤러에 닿는다. 라우팅과 같은 풀린 경로로 판정한다.
        String path = UrlPathHelper.defaultInstance.getPathWithinApplication(request);
        if (EXEMPT_PATH.equals(path)) {
            return true;
        }
        return PROTECTED_PATHS.stream().noneMatch(pattern -> pathMatcher.match(pattern, path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String clusterId = canonicalOrNull(request.getHeader(CLUSTER_HEADER));
        if (!isValid(clusterId, request.getHeader(TOKEN_HEADER))) {
            log.warn("[InternalTokenFilter] 내부 클러스터 인증 실패: uri={}, cluster={}, remote={}",
                    request.getRequestURI(), clusterId, request.getRemoteAddr());
            response.setStatus(ErrorCode.INTERNAL_CLUSTER_AUTHENTICATION_FAILED.getHttpStatus().value());
            response.setContentType("application/json;charset=UTF-8");
            objectMapper.writeValue(
                    response.getWriter(),
                    ErrorResponse.of(ErrorCode.INTERNAL_CLUSTER_AUTHENTICATION_FAILED)
            );
            return;
        }
        request.setAttribute(VERIFIED_CLUSTER_ATTRIBUTE, clusterId);
        chain.doFilter(request, response);
    }

    private String canonicalOrNull(String rawClusterId) {
        try {
            return ClusterId.canonical(rawClusterId);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private boolean isValid(String clusterId, String sent) {
        if (clusterId == null || sent == null) {
            return false;
        }
        byte[] expected = expectedByCluster.get(clusterId);
        return expected != null
                && MessageDigest.isEqual(sent.getBytes(StandardCharsets.UTF_8), expected);
    }
}
