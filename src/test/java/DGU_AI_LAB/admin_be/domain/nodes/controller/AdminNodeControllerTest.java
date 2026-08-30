package DGU_AI_LAB.admin_be.domain.nodes.controller;

import DGU_AI_LAB.admin_be.domain.nodes.dto.response.NodeResponseDTO;
import DGU_AI_LAB.admin_be.domain.nodes.service.NodeQueryService;
import DGU_AI_LAB.admin_be.support.WebMvcTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        value = AdminNodeController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class}
)
class AdminNodeControllerTest extends WebMvcTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NodeQueryService nodeQueryService;

    @Nested
    @DisplayName("GET /api/admin/nodes")
    class GetAllNodes {

        @Test
        @DisplayName("전체 노드 목록을 200 OK로 반환한다")
        void getAllNodes_returns200WithList() throws Exception {
            NodeResponseDTO node = NodeResponseDTO.builder()
                    .nodeId("farm1")
                    .resourceGroupName("3090ti")
                    .memorySizeGB(128)
                    .cpuCoreCount(32)
                    .numberGpu(4)
                    .build();
            when(nodeQueryService.getAllNodes()).thenReturn(List.of(node));

            mockMvc.perform(get("/api/admin/nodes").contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].nodeId").value("farm1"))
                    .andExpect(jsonPath("$.data[0].resourceGroupName").value("3090ti"))
                    .andExpect(jsonPath("$.data[0].numberGpu").value(4));
        }

        @Test
        @DisplayName("노드가 없으면 빈 배열을 200 OK로 반환한다")
        void getAllNodes_returns200WithEmptyList_whenNoNodes() throws Exception {
            when(nodeQueryService.getAllNodes()).thenReturn(List.of());

            mockMvc.perform(get("/api/admin/nodes").contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(0));
        }
    }
}
