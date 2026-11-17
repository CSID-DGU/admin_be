package DGU_AI_LAB.admin_be.domain.adminActionLog;

import DGU_AI_LAB.admin_be.domain.users.entity.*;
import DGU_AI_LAB.admin_be.global.auth.CustomUserDetailsService;
import DGU_AI_LAB.admin_be.global.auth.jwt.JwtProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminActionLogSecurityTest {
    @Autowired MockMvc mvc;
    @Autowired JwtProvider jwtProvider;
    @MockitoBean CustomUserDetailsService users;
    @MockitoBean DGU_AI_LAB.admin_be.domain.users.service.AdminUserService adminUsers;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper mapper;
    @Autowired @org.springframework.beans.factory.annotation.Qualifier("requestMappingHandlerMapping")
    org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping mappings;

    @Test
    void everyRequiredActionHasExactlyOneMapping() {
        var actions = mappings.getHandlerMethods().values().stream()
                .map(method -> method.getMethodAnnotation(AdminAction.class))
                .filter(java.util.Objects::nonNull).map(AdminAction::value).toList();
        org.assertj.core.api.Assertions.assertThat(actions)
                .containsExactlyInAnyOrder(AdminActionType.values());
    }

    @Test
    void realRoleRequestIsEventuallyVisibleThroughReadApi() throws Exception {
        User user = mock(User.class);
        when(user.getUserId()).thenReturn(77L);
        when(user.getRole()).thenReturn(Role.ADMIN);
        when(users.loadUserEntityById(77L)).thenReturn(user);
        String authorization = "Bearer " + jwtProvider.getIssueToken(77L, true);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/admin/users/987654321/role")
                .header("Authorization", authorization).contentType("application/json").content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk());
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        com.fasterxml.jackson.databind.JsonNode item = null;
        while (System.nanoTime() < deadline) {
            String json = mvc.perform(get("/api/admin/action-logs").header("Authorization", authorization)
                    .param("targetType", "USER").param("targetId", "987654321").param("requestedRole", "ADMIN"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            var items = mapper.readTree(json).at("/data/items");
            if (!items.isEmpty()) { item = items.get(0); break; }
            Thread.sleep(25);
        }
        org.assertj.core.api.Assertions.assertThat(item).isNotNull();
        org.assertj.core.api.Assertions.assertThat(item.get("adminId").asLong()).isEqualTo(77L);
        org.assertj.core.api.Assertions.assertThat(item.get("result").asText()).isEqualTo("SUCCEEDED");
    }


    @Test
    void onlyAdminCanReadListAndDetails() throws Exception {
        mvc.perform(get("/api/admin/action-logs")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/action-logs/1")).andExpect(status().isUnauthorized());
        User user = mock(User.class);
        when(user.getUserId()).thenReturn(7L);
        when(user.getRole()).thenReturn(Role.USER);
        when(users.loadUserEntityById(7L)).thenReturn(user);
        String token = jwtProvider.getIssueToken(7L, true);
        mvc.perform(get("/api/admin/action-logs").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/action-logs/1").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        when(user.getRole()).thenReturn(Role.ADMIN);
        mvc.perform(get("/api/admin/action-logs").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
    }
}
