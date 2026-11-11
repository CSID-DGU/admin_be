package DGU_AI_LAB.admin_be.domain.monitoring.controller;

import DGU_AI_LAB.admin_be.domain.monitoring.service.AdminGpuUsageService;
import DGU_AI_LAB.admin_be.support.WebMvcTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.eq;

@WebMvcTest(
        value = AdminGpuUsageController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class}
)
class AdminGpuUsageControllerTest extends WebMvcTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminGpuUsageService adminGpuUsageService;

    @Test
    @DisplayName("node를 생략하면 전체 노드(null)로 조회하고 200과 노드 목록을 준다")
    void allNodesWhenNodeOmitted() throws Exception {
        when(adminGpuUsageService.getGpuUsage(isNull())).thenReturn(List.of(
                new DGU_AI_LAB.admin_be.domain.monitoring.dto.response.AdminGpuUsageResponseDTO(
                        "farm6", false, List.of())));

        mockMvc.perform(get("/api/admin/gpu-usage"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].nodeName").value("farm6"))
                .andExpect(jsonPath("$.data[0].processesAvailable").value(false));
    }

    @Test
    @DisplayName("node 파라미터를 서비스에 그대로 넘긴다")
    void passesNodeParameter() throws Exception {
        when(adminGpuUsageService.getGpuUsage(eq("farm6"))).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/gpu-usage").param("node", "farm6"))
                .andExpect(status().isOk());

        verify(adminGpuUsageService).getGpuUsage("farm6");
    }
}
