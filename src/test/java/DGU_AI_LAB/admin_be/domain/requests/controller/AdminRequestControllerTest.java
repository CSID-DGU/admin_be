package DGU_AI_LAB.admin_be.domain.requests.controller;

import DGU_AI_LAB.admin_be.domain.requests.service.AdminRequestCommandService;
import DGU_AI_LAB.admin_be.domain.requests.service.AdminRequestQueryService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import DGU_AI_LAB.admin_be.domain.requests.service.OperationJobService;
import DGU_AI_LAB.admin_be.domain.requests.service.PodMigrationService;
import DGU_AI_LAB.admin_be.domain.requests.service.RequestExpiryService;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@WebMvcTest(
        value = AdminRequestController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class}
)
class AdminRequestControllerTest extends WebMvcTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean private AdminRequestCommandService adminRequestCommandService;
    @MockitoBean private AdminRequestQueryService adminRequestQueryService;
    @MockitoBean private PodMigrationService podMigrationService;
    @MockitoBean private OperationJobService operationJobService;
    @MockitoBean private RequestExpiryService requestExpiryService;

    @Test
    @DisplayName("POST /{id}/approval: 경로의 신청 번호로 생성 작업을 등록하고 202를 반환한다")
    void approvalIsAccepted() throws Exception {
        mockMvc.perform(post("/api/admin/requests/12/approval")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"imageId\": 3, \"resourceGroupId\": 2, \"adminComment\": \"ok\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value(202));

        verify(adminRequestCommandService).approveRequest(argThat(dto ->
                dto.requestId() == 12L && dto.imageId() == 3L && dto.resourceGroupId() == 2));
    }

    @Test
    @DisplayName("POST /{id}/approval: 이미지가 없으면 400이고 승인하지 않는다")
    void approvalValidatesBody() throws Exception {
        mockMvc.perform(post("/api/admin/requests/12/approval")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resourceGroupId\": 2}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(adminRequestCommandService);
    }

    @Test
    @DisplayName("POST /{id}/rejection: 경로의 신청 번호로 거절하고 200을 반환한다")
    void rejection() throws Exception {
        mockMvc.perform(post("/api/admin/requests/13/rejection")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"adminComment\": \"사유\"}"))
                .andExpect(status().isOk());
        verify(adminRequestCommandService).rejectRequest(argThat(dto -> dto.requestId() == 13L && "사유".equals(dto.adminComment())));
    }

    @Test
    @DisplayName("POST /{id}/migrations: 마이그레이션 작업을 등록하고 202를 반환한다")
    void migrationIsAccepted() throws Exception {
        mockMvc.perform(post("/api/admin/requests/14/migrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nodes\": [\"farm2\", \"farm7\"], \"force\": true}"))
                .andExpect(status().isAccepted());
        verify(podMigrationService).startMigration(eq(14L), argThat(dto -> Boolean.TRUE.equals(dto.force()) && dto.nodes().size() == 2));
    }

    @Test
    @DisplayName("DELETE /{id}/container: 그 신청의 컨테이너만 회수한다 — 사용자 단위 정리 경로를 타지 않는다")
    void containerDeleteIsScopedToOneRequest() throws Exception {
        mockMvc.perform(delete("/api/admin/requests/15/container"))
                .andExpect(status().isAccepted());

        // 경로의 신청 번호 하나로만 회수한다. 예전에는 화면의 이 버튼이 계정 회수 API를 불러
        // 그 사용자의 컨테이너가 전부 사라졌다 — 그 회귀를 여기서 막는다.
        verify(requestExpiryService).deleteContainerByAdmin(15L);
        verifyNoInteractions(adminRequestCommandService);
    }
}
