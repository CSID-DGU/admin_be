package DGU_AI_LAB.admin_be.global.auth.jwt;

import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.global.auth.CustomUserDetailsService;
import DGU_AI_LAB.admin_be.support.LogCaptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * JwtAuthenticationFilter가 Authorization 헤더 원문이나 AccessToken 값을
 * 로그로 남기지 않는지 검증한다. 로그로 흘러간 토큰은 그대로 계정 탈취 수단이 된다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JwtAuthenticationFilterLoggingTest {

    private static final String RAW_TOKEN = "eyJhbGciOiJIUzI1NiJ9.super-secret-token-payload.signature";
    private static final String RAW_HEADER = "Bearer " + RAW_TOKEN;

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private CustomUserDetailsService customUserDetailsService;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtProvider, customUserDetailsService, redisTemplate);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("인증 성공 시 AccessToken과 Authorization 헤더 원문이 로그에 남지 않는다")
    void doesNotLogTokenOnSuccess() throws Exception {
        User user = User.builder()
                .email("test@dgu.ac.kr")
                .password("encoded")
                .name("홍길동")
                .studentId("2021001234")
                .phone("010-1234-5678")
                .department("컴퓨터공학과")
                .build();

        when(jwtProvider.getSubject(RAW_TOKEN)).thenReturn(1L);
        when(customUserDetailsService.loadUserEntityById(1L)).thenReturn(user);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/requests/me");
        request.addHeader("Authorization", RAW_HEADER);

        try (LogCaptor logCaptor = LogCaptor.forClass(JwtAuthenticationFilter.class)) {
            filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

            assertThat(logCaptor.joined())
                    .doesNotContain(RAW_TOKEN)
                    .doesNotContain(RAW_HEADER);
        }

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }

    @Test
    @DisplayName("Authorization 헤더 형식이 잘못된 경우에도 헤더 원문이 로그에 남지 않는다")
    void doesNotLogHeaderOnMalformedHeader() throws Exception {
        String malformedHeader = "Token " + RAW_TOKEN;

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/requests/me");
        request.addHeader("Authorization", malformedHeader);

        try (LogCaptor logCaptor = LogCaptor.forClass(JwtAuthenticationFilter.class)) {
            filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

            assertThat(logCaptor.joined())
                    .doesNotContain(RAW_TOKEN)
                    .doesNotContain(malformedHeader);
        }
    }
}
