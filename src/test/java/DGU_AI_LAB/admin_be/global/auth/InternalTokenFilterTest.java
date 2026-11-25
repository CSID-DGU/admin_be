package DGU_AI_LAB.admin_be.global.auth;

import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.global.cluster.ClusterClientProperties;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class InternalTokenFilterTest {

    private static final String FARM_TOKEN = "farm-s3cret-token";
    private static final String LAB_TOKEN = "lab-s3cret-token";

    private final FilterChain chain = mock(FilterChain.class);

    private ClusterClientProperties properties() {
        return new ClusterClientProperties(Map.of(
                "FARM", cluster(FARM_TOKEN),
                "LAB", cluster(LAB_TOKEN)
        ), true);
    }

    private ClusterClientProperties.Cluster cluster(String token) {
        return new ClusterClientProperties.Cluster(
                "http://config.invalid", token, 1, 2,
                false, "/not/read/by/filter", "default"
        );
    }

    private Result run(String uri, String clusterId, String token) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        if (clusterId != null) {
            request.addHeader(InternalTokenFilter.CLUSTER_HEADER, clusterId);
        }
        if (token != null) {
            request.addHeader(InternalTokenFilter.TOKEN_HEADER, token);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        new InternalTokenFilter(properties()).doFilter(request, response, chain);
        return new Result(request, response);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/requests/config/alice", "/api/requests/config/by-request/1", "/api/internal/slack/notify"})
    @DisplayName("보호 경로는 클러스터 또는 토큰이 없으면 403으로 거절한다")
    void rejectsMissingClusterOrToken(String uri) throws Exception {
        assertThat(run(uri, null, FARM_TOKEN).response().getStatus()).isEqualTo(403);
        assertThat(run(uri, "FARM", null).response().getStatus()).isEqualTo(403);

        verify(chain, never()).doFilter(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/requests/%63onfig/by-request/1", "/api/%72equests/config/alice",
            "/api/%69nternal/slack/notify", "/api/requests/config/%63heck-username/../by-request/1"})
    @DisplayName("글자를 퍼센트 인코딩한 보호 경로도 인증 정보가 없으면 거절한다")
    void rejectsPercentEncodedProtectedPath(String uri) throws Exception {
        MockHttpServletResponse response = run(uri, null, null).response();

        assertThat(response.getStatus()).isEqualTo(403);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("FARM 클러스터 헤더에 LAB 토큰을 붙이면 거절한다")
    void rejectsTokenBelongingToAnotherCluster() throws Exception {
        MockHttpServletResponse response = run(
                "/api/requests/config/by-request/1", "FARM", LAB_TOKEN
        ).response();

        assertThat(response.getStatus())
                .isEqualTo(ErrorCode.INTERNAL_CLUSTER_AUTHENTICATION_FAILED.getHttpStatus().value());
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("설정되지 않은 클러스터는 올바른 다른 클러스터 토큰을 보내도 거절한다")
    void rejectsUnknownCluster() throws Exception {
        MockHttpServletResponse response = run(
                "/api/requests/config/by-request/1", "UNKNOWN", FARM_TOKEN
        ).response();

        assertThat(response.getStatus()).isEqualTo(403);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("클러스터와 그 클러스터 토큰이 모두 맞으면 canonical clusterId를 request attribute로 넘긴다")
    void passesBoundClusterAndToken() throws Exception {
        Result result = run("/api/requests/config/by-request/1", " farm ", FARM_TOKEN);

        assertThat(result.request().getAttribute(InternalTokenFilter.VERIFIED_CLUSTER_ATTRIBUTE))
                .isEqualTo("FARM");
        verify(chain).doFilter(any(), any());
    }

    @Test
    @DisplayName("provi.clusters가 비어 있으면 어떤 토큰도 통과시키지 않는다")
    void rejectsWhenClustersAreNotConfigured() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/requests/config/alice");
        request.addHeader(InternalTokenFilter.CLUSTER_HEADER, "FARM");
        request.addHeader(InternalTokenFilter.TOKEN_HEADER, FARM_TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();

        new InternalTokenFilter(new ClusterClientProperties(Map.of(), false)).doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("LAB profile과 token이 있어도 feature flag가 꺼져 있으면 LAB 내부 요청을 거절한다")
    void rejectsLabTokenWhileLabFeatureIsDisabled() throws Exception {
        ClusterClientProperties disabled = new ClusterClientProperties(Map.of(
                "FARM", cluster(FARM_TOKEN),
                "LAB", cluster(LAB_TOKEN)
        ), false);
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET", "/api/requests/config/by-request/1");
        request.addHeader(InternalTokenFilter.CLUSTER_HEADER, "LAB");
        request.addHeader(InternalTokenFilter.TOKEN_HEADER, LAB_TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();

        new InternalTokenFilter(disabled).doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("LAB feature flag가 꺼져 있어도 기존 FARM 내부 요청은 계속 허용한다")
    void acceptsFarmTokenWhileLabFeatureIsDisabled() throws Exception {
        ClusterClientProperties disabled = new ClusterClientProperties(Map.of(
                "FARM", cluster(FARM_TOKEN),
                "LAB", cluster(LAB_TOKEN)
        ), false);
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET", "/api/requests/config/by-request/1");
        request.addHeader(InternalTokenFilter.CLUSTER_HEADER, "FARM");
        request.addHeader(InternalTokenFilter.TOKEN_HEADER, FARM_TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();

        new InternalTokenFilter(disabled).doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("두 클러스터에 같은 내부 토큰이 설정되면 시작 단계에서 거절한다")
    void rejectsDuplicateTokensAcrossClusters() {
        ClusterClientProperties duplicated = new ClusterClientProperties(Map.of(
                "FARM", cluster(FARM_TOKEN),
                "LAB", cluster(FARM_TOKEN)
        ), true);

        assertThatThrownBy(() -> new InternalTokenFilter(duplicated))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("서로 다른 값");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/requests/config/check-username", "/api/auth/login", "/api/requests"})
    @DisplayName("JWT로 보호하는 이름 중복 확인과 내부 보호 대상이 아닌 경로는 클러스터 헤더 없이 통과한다")
    void ignoresUnprotectedPaths(String uri) throws Exception {
        run(uri, null, null);

        verify(chain).doFilter(any(), any());
    }

    private record Result(MockHttpServletRequest request, MockHttpServletResponse response) {
    }
}
