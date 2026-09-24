package DGU_AI_LAB.admin_be.global.auth;

import DGU_AI_LAB.admin_be.error.ErrorCode;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class InternalTokenFilterTest {

    private static final String TOKEN = "s3cret-token";

    private final FilterChain chain = mock(FilterChain.class);

    private MockHttpServletResponse run(String configuredToken, String uri, String sentToken) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        if (sentToken != null) {
            request.addHeader(InternalTokenFilter.HEADER, sentToken);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        new InternalTokenFilter(configuredToken).doFilter(request, response, chain);
        return response;
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/requests/config/alice", "/api/requests/config/by-request/1", "/api/internal/slack/notify"})
    @DisplayName("보호 경로는 토큰이 없으면 401로 거절하고 컨트롤러까지 넘기지 않는다")
    void rejectsMissingToken(String uri) throws Exception {
        MockHttpServletResponse response = run(TOKEN, uri, null);

        assertThat(response.getStatus()).isEqualTo(ErrorCode.UNAUTHORIZED.getHttpStatus().value());
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("보호 경로는 토큰이 틀리면 거절한다")
    void rejectsWrongToken() throws Exception {
        MockHttpServletResponse response = run(TOKEN, "/api/requests/config/by-request/1", "wrong");

        assertThat(response.getStatus()).isEqualTo(401);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("보호 경로는 토큰이 맞으면 통과시킨다")
    void passesValidToken() throws Exception {
        run(TOKEN, "/api/requests/config/by-request/1", TOKEN);

        verify(chain).doFilter(any(), any());
    }

    @Test
    @DisplayName("서버에 토큰이 설정돼 있지 않으면 빈 헤더를 보내도 거절한다(fail-closed)")
    void rejectsWhenTokenNotConfigured() throws Exception {
        MockHttpServletResponse response = run("", "/api/requests/config/alice", "");

        assertThat(response.getStatus()).isEqualTo(401);
        verify(chain, never()).doFilter(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/requests/config/check-username", "/api/auth/login", "/api/requests"})
    @DisplayName("이름 중복 확인과 보호 대상이 아닌 경로는 토큰 없이 통과한다")
    void ignoresUnprotectedPaths(String uri) throws Exception {
        run(TOKEN, uri, null);

        verify(chain).doFilter(any(), any());
    }
}
