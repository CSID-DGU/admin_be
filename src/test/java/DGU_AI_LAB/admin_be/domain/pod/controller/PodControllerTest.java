package DGU_AI_LAB.admin_be.domain.pod.controller;

import DGU_AI_LAB.admin_be.domain.pod.dto.response.PodResponseDTO;
import DGU_AI_LAB.admin_be.domain.pod.service.PodQueryService;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
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
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        value = PodController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class}
)
class PodControllerTest extends WebMvcTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PodQueryService podQueryService;

    @Nested
    @DisplayName("GET /api/admin/pods")
    class GetPods {

        @Test
        @DisplayName("Pod 이름 목록을 200 OK로 반환한다")
        void getPods_returns200WithNameList() throws Exception {
            when(podQueryService.getPodNames()).thenReturn(List.of("pod-a", "pod-b"));

            mockMvc.perform(get("/api/admin/pods").contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0]").value("pod-a"))
                    .andExpect(jsonPath("$[1]").value("pod-b"));
        }

        @Test
        @DisplayName("Pod가 없으면 빈 배열을 200 OK로 반환한다")
        void getPods_returns200WithEmptyList_whenNoPods() throws Exception {
            when(podQueryService.getPodNames()).thenReturn(List.of());

            mockMvc.perform(get("/api/admin/pods").contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }
    }

    @Nested
    @DisplayName("GET /api/admin/pods/{podName}")
    class GetPodDetail {

        @Test
        @DisplayName("Pod가 존재하면 상세 정보를 200 OK로 반환한다")
        void getPodDetail_returns200WithDetail_whenPodExists() throws Exception {
            PodResponseDTO dto = PodResponseDTO.builder()
                    .name("pod-a")
                    .namespace("ailab-infra")
                    .status("Running")
                    .labels(Map.of())
                    .annotations(Map.of())
                    .containers(List.of())
                    .volumes(List.of())
                    .build();
            when(podQueryService.getPodDetail("pod-a")).thenReturn(dto);

            mockMvc.perform(get("/api/admin/pods/pod-a").contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("pod-a"))
                    .andExpect(jsonPath("$.status").value("Running"));
        }

        @Test
        @DisplayName("Pod가 존재하지 않으면 404와 에러 메시지를 반환한다")
        void getPodDetail_returns404_whenPodNotFound() throws Exception {
            when(podQueryService.getPodDetail("missing-pod"))
                    .thenThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

            mockMvc.perform(get("/api/admin/pods/missing-pod").contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.message").value(ErrorCode.RESOURCE_NOT_FOUND.getMessage()));
        }
    }
}
