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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;

@WebMvcTest(
        value = AdminRequestChangeController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class}
)
class AdminRequestChangeControllerTest extends WebMvcTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminRequestCommandService adminRequestCommandService;

    @MockitoBean
    private AdminRequestQueryService adminRequestQueryService;

    @Nested
    @DisplayName("POST /api/admin/change-requests/{id}/approval")
    class ApproveModification {

        @Test
        @DisplayName("경로의 변경 요청 번호로 승인하고 200과 SuccessResponse를 반환한다")
        void approvesByPathId() throws Exception {
            mockMvc.perform(post("/api/admin/change-requests/7/approval")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"adminComment\": \"승인합니다.\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200))
                    .andExpect(jsonPath("$.data").isEmpty());

            verify(adminRequestCommandService).approveModification(isNull(),
                    argThat(dto -> dto.changeRequestId() == 7L && "승인합니다.".equals(dto.adminComment())));
        }

        @Test
        @DisplayName("adminComment가 없으면 400을 반환한다")
        void returns400WhenCommentMissing() throws Exception {
            mockMvc.perform(post("/api/admin/change-requests/7/approval")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("POST /api/admin/change-requests/{id}/rejection")
    class RejectModification {

        @Test
        @DisplayName("경로의 변경 요청 번호로 거절하고 200을 반환한다")
        void rejectsByPathId() throws Exception {
            mockMvc.perform(post("/api/admin/change-requests/8/rejection")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"adminComment\": \"거절합니다.\"}"))
                    .andExpect(status().isOk());

            verify(adminRequestCommandService).rejectModification(isNull(),
                    argThat(dto -> dto.changeRequestId() == 8L && "거절합니다.".equals(dto.adminComment())));
        }

        @Test
        @DisplayName("adminComment가 없으면 400을 반환한다")
        void returns400WhenCommentMissing() throws Exception {
            mockMvc.perform(post("/api/admin/change-requests/8/rejection")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    @DisplayName("옛 경로 PATCH /api/admin/requests/change/approve는 없다")
    void oldPathIsGone() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/admin/requests/change/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"changeRequestId\": 1, \"adminComment\": \"x\"}"))
                .andExpect(result -> org.assertj.core.api.Assertions.assertThat(result.getResponse().getStatus()).isIn(404, 405, 500));
    }
}
