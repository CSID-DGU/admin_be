package DGU_AI_LAB.admin_be.domain.adminActionLog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import DGU_AI_LAB.admin_be.domain.users.entity.Role;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.service.AdminUserService;
import DGU_AI_LAB.admin_be.global.auth.CustomUserDetailsService;
import DGU_AI_LAB.admin_be.global.auth.jwt.JwtProvider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("관리자 작업 기록 접근 제어와 통합 흐름")
class AdminActionLogSecurityTest {
    @Autowired MockMvc mvc;
    @Autowired JwtProvider jwtProvider;
    @MockitoBean CustomUserDetailsService users;
    @MockitoBean AdminUserService adminUsers;
    @Autowired ObjectMapper mapper;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping mappings;

    @Test
    @DisplayName("19개 행동이 각각 하나의 컨트롤러 메서드에 매핑된다")
    void everyRequiredActionHasExactlyOneMapping() {
        var actions =
                mappings.getHandlerMethods().values().stream()
                        .map(method -> method.getMethodAnnotation(AdminAction.class))
                        .filter(java.util.Objects::nonNull)
                        .map(AdminAction::value)
                        .toList();
        assertThat(actions).containsExactlyInAnyOrder(AdminActionType.values());
    }

    @Test
    @DisplayName("인증된 역할 변경 요청이 비동기 저장된 뒤 조회 API에 나타난다")
    void realRoleRequestIsEventuallyVisibleThroughReadApi() throws Exception {
        User user = mock(User.class);
        when(user.getUserId()).thenReturn(77L);
        when(user.getRole()).thenReturn(Role.ADMIN);
        when(users.loadUserEntityById(77L)).thenReturn(user);
        String authorization = "Bearer " + jwtProvider.getIssueToken(77L, true);
        mvc.perform(
                        patch("/api/admin/users/987654321/role")
                                .header("Authorization", authorization)
                                .contentType("application/json")
                                .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk());
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        JsonNode item = null;
        while (System.nanoTime() < deadline) {
            String json =
                    mvc.perform(
                                    get("/api/admin/action-logs")
                                            .header("Authorization", authorization)
                                            .param("targetType", "USER")
                                            .param("targetId", "987654321")
                                            .param("requestedRole", "ADMIN"))
                            .andExpect(status().isOk())
                            .andReturn()
                            .getResponse()
                            .getContentAsString();
            var items = mapper.readTree(json).at("/data/items");
            if (!items.isEmpty()) {
                item = items.get(0);
                break;
            }
            Thread.sleep(25);
        }
        assertThat(item).isNotNull();
        assertThat(item.get("adminId").asLong()).isEqualTo(77L);
        assertThat(item.get("result").asText()).isEqualTo("SUCCEEDED");
    }

    @Test
    @DisplayName("미인증·일반 사용자 접근은 거절하고 관리자 조회는 허용한다")
    void onlyAdminCanReadListAndDetails() throws Exception {
        mvc.perform(get("/api/admin/action-logs")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/action-logs/1")).andExpect(status().isUnauthorized());
        User user = mock(User.class);
        when(user.getUserId()).thenReturn(7L);
        when(user.getRole()).thenReturn(Role.USER);
        when(users.loadUserEntityById(7L)).thenReturn(user);
        String token = jwtProvider.getIssueToken(7L, true);
        mvc.perform(get("/api/admin/action-logs").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/action-logs/1").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        when(user.getRole()).thenReturn(Role.ADMIN);
        mvc.perform(get("/api/admin/action-logs").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
}
