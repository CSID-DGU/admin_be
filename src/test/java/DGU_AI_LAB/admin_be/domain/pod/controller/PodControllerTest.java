package DGU_AI_LAB.admin_be.domain.pod.controller;

import DGU_AI_LAB.admin_be.domain.pod.dto.response.PodResponseDTO;
import DGU_AI_LAB.admin_be.domain.pod.dto.response.PodEventDTO;
import DGU_AI_LAB.admin_be.domain.pod.service.PodQueryService;
import DGU_AI_LAB.admin_be.domain.pod.service.PodStatusCache;
import DGU_AI_LAB.admin_be.domain.pod.service.PodStatusSnapshot;
import DGU_AI_LAB.admin_be.domain.requests.service.PodService;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

    @MockitoBean
    private PodService podService;

    @MockitoBean
    private PodStatusCache podStatusCache;

    @Nested
    @DisplayName("POST /api/admin/clusters/{clusterId}/pods/status-refresh")
    class RefreshPodStatus {

        @Test
        @DisplayName("상태를 다시 읽고 읽은 시각을 200 OK로 돌려준다")
        void refreshPodStatus_returnsCheckedAt() throws Exception {
            when(podStatusCache.refreshUnlessFresh("FARM")).thenReturn(
                    new PodStatusSnapshot(Map.of(), List.of(),
                            Map.of("FARM", LocalDateTime.of(2026, 10, 15, 21, 25, 30))));

            mockMvc.perform(post("/api/admin/clusters/FARM/pods/status-refresh"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.statusCheckedAt").value("2026-10-15T21:25:30"));
        }
    }

    @Nested
    @DisplayName("GET /api/admin/clusters/{clusterId}/pods")
    class GetPods {

        @Test
        @DisplayName("Pod 이름 목록을 200 OK로 반환한다")
        void getPods_returns200WithNameList() throws Exception {
            when(podQueryService.getPodNames("FARM")).thenReturn(List.of("pod-a", "pod-b"));

            mockMvc.perform(get("/api/admin/clusters/FARM/pods").contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0]").value("pod-a"))
                    .andExpect(jsonPath("$[1]").value("pod-b"));
        }

        @Test
        @DisplayName("Pod가 없으면 빈 배열을 200 OK로 반환한다")
        void getPods_returns200WithEmptyList_whenNoPods() throws Exception {
            when(podQueryService.getPodNames("LAB")).thenReturn(List.of());

            mockMvc.perform(get("/api/admin/clusters/LAB/pods").contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        @DisplayName("등록되지 않은 클러스터는 500 대신 400으로 반환한다")
        void getPods_returns400ForUnknownCluster() throws Exception {
            mockMvc.perform(get("/api/admin/clusters/UNKNOWN/pods"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message").value(ErrorCode.INVALID_INPUT_VALUE.getMessage()));

            verify(podQueryService, org.mockito.Mockito.never()).getPodNames(org.mockito.ArgumentMatchers.anyString());
        }
    }

    @Nested
    @DisplayName("GET /api/admin/clusters/{clusterId}/pods/{podName}")
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
            when(podQueryService.getPodDetail("FARM", "pod-a")).thenReturn(dto);

            mockMvc.perform(get("/api/admin/clusters/FARM/pods/pod-a").contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("pod-a"))
                    .andExpect(jsonPath("$.status").value("Running"));
        }

        @Test
        @DisplayName("Pod가 존재하지 않으면 404와 에러 메시지를 반환한다")
        void getPodDetail_returns404_whenPodNotFound() throws Exception {
            when(podQueryService.getPodDetail("LAB", "missing-pod"))
                    .thenThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

            mockMvc.perform(get("/api/admin/clusters/LAB/pods/missing-pod").contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.message").value(ErrorCode.RESOURCE_NOT_FOUND.getMessage()));
        }
    }

    @Nested
    @DisplayName("기존 /api/admin/pods FARM 호환 경로")
    class LegacyFarmAlias {

        @Test
        @DisplayName("목록과 상태 갱신을 FARM으로 위임한다")
        void listAndRefreshDelegateToFarm() throws Exception {
            when(podQueryService.getPodNames("FARM")).thenReturn(List.of("farm-pod"));
            when(podStatusCache.refreshUnlessFresh("FARM")).thenReturn(
                    new PodStatusSnapshot(Map.of(), List.of(),
                            Map.of("FARM", LocalDateTime.of(2026, 10, 15, 21, 25, 30))));

            mockMvc.perform(get("/api/admin/pods"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0]").value("farm-pod"));
            mockMvc.perform(post("/api/admin/pods/status-refresh"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.statusCheckedAt").value("2026-10-15T21:25:30"));
        }

        @Test
        @DisplayName("상세·로그·이벤트 조회를 FARM으로 위임한다")
        void readsDelegateToFarm() throws Exception {
            PodResponseDTO detail = PodResponseDTO.builder()
                    .name("farm-pod").namespace("farm").status("Running")
                    .labels(Map.of()).annotations(Map.of()).containers(List.of()).volumes(List.of()).build();
            PodEventDTO event = PodEventDTO.builder().type("Normal").reason("Scheduled").build();
            when(podQueryService.getPodDetail("FARM", "farm-pod")).thenReturn(detail);
            when(podQueryService.getPodLogs("FARM", "farm-pod", null)).thenReturn("farm-log");
            when(podQueryService.getPodEvents("FARM", "farm-pod")).thenReturn(List.of(event));

            mockMvc.perform(get("/api/admin/pods/farm-pod"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("farm-pod"));
            mockMvc.perform(get("/api/admin/pods/farm-pod/logs"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.logs").value("farm-log"));
            mockMvc.perform(get("/api/admin/pods/farm-pod/events"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].reason").value("Scheduled"));
        }

        @Test
        @DisplayName("고아 Pod 삭제를 FARM으로 위임한다")
        void deleteDelegatesToFarm() throws Exception {
            mockMvc.perform(delete("/api/admin/pods/farm-pod"))
                    .andExpect(status().isOk());

            verify(podService).deleteOrphanPod("FARM", "farm-pod");
        }
    }
}
