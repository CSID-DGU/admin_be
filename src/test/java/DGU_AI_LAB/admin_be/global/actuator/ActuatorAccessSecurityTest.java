package DGU_AI_LAB.admin_be.global.actuator;

import DGU_AI_LAB.admin_be.domain.users.entity.Role;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.global.auth.CustomUserDetailsService;
import DGU_AI_LAB.admin_be.global.auth.jwt.JwtProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** health·info 말고 노출된 actuator 경로는 관리자만 볼 수 있어야 한다. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "management.endpoints.web.exposure.include=health,info,metrics")
class ActuatorAccessSecurityTest {

    private static final long USER_ID = 1L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    @DisplayName("로그인하지 않으면 /actuator/metrics는 401이다")
    void metrics_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("일반 사용자는 /actuator/metrics를 볼 수 없다(403)")
    void metrics_user_returns403() throws Exception {
        mockMvc.perform(metricsAs(Role.USER)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("관리자는 /actuator/metrics를 본다")
    void metrics_admin_returns200() throws Exception {
        mockMvc.perform(metricsAs(Role.ADMIN)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("로그인하지 않아도 /actuator/info는 열려 있다")
    void info_unauthenticated_returns200() throws Exception {
        mockMvc.perform(get("/actuator/info")).andExpect(status().isOk());
    }

    private MockHttpServletRequestBuilder metricsAs(Role role) {
        User user = mock(User.class);
        when(user.getUserId()).thenReturn(USER_ID);
        when(user.getRole()).thenReturn(role);
        when(customUserDetailsService.loadUserEntityById(USER_ID)).thenReturn(user);
        return get("/actuator/metrics")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtProvider.getIssueToken(USER_ID, true));
    }
}
