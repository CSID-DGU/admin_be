package DGU_AI_LAB.admin_be.global.auth;

import DGU_AI_LAB.admin_be.global.config.RuntimeModeProperties;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("SHADOW HTTP mutation gate")
class ShadowModeMutationFilterTest {

    @Test
    @DisplayName("SHADOW의 POST는 인증이나 컨트롤러 전에 503으로 거절한다")
    void rejectsMutationInShadow() throws Exception {
        var filter = new ShadowModeMutationFilter(
                new RuntimeModeProperties(RuntimeModeProperties.Mode.SHADOW));
        var request = new MockHttpServletRequest("POST", "/api/requests");
        var response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).contains(ShadowModeMutationFilter.ERROR_CODE);
        assertThat(response.getHeader("Retry-After")).isEqualTo("60");
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    @DisplayName("SHADOW의 GET은 조회 경로로 전달한다")
    void allowsReadInShadow() throws Exception {
        var filter = new ShadowModeMutationFilter(
                new RuntimeModeProperties(RuntimeModeProperties.Mode.SHADOW));
        var request = new MockHttpServletRequest("GET", "/actuator/health");
        var response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("ACTIVE는 쓰기 요청을 기존 security/controller chain으로 전달한다")
    void allowsMutationInActive() throws Exception {
        var filter = new ShadowModeMutationFilter(
                new RuntimeModeProperties(RuntimeModeProperties.Mode.ACTIVE));
        var request = new MockHttpServletRequest("POST", "/api/requests");
        var response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }
}
