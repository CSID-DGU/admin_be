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
    @DisplayName("PATCH /api/admin/requests/change/approve")
    class ApproveModification {

        @Test
        @DisplayName("변경 요청 승인 시 200 OK와 SuccessResponse 형식의 body를 반환한다")
        void approveModification_returns200WithSuccessResponseBody() throws Exception {
            doNothing().when(adminRequestCommandService).approveModification(anyLong(), any());

            mockMvc.perform(patch("/api/admin/requests/change/approve")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"changeRequestId\": 1, \"adminComment\": \"승인합니다.\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200))
                    .andExpect(jsonPath("$.message").exists())
                    .andExpect(jsonPath("$.data").isEmpty());
        }

        @Test
        @DisplayName("changeRequestId가 없으면 400 Bad Request를 반환한다")
        void approveModification_returns400_whenChangeRequestIdMissing() throws Exception {
            mockMvc.perform(patch("/api/admin/requests/change/approve")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"adminComment\": \"승인합니다.\"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("adminComment가 없으면 400 Bad Request를 반환한다")
        void approveModification_returns400_whenAdminCommentMissing() throws Exception {
            mockMvc.perform(patch("/api/admin/requests/change/approve")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"changeRequestId\": 1}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("응답 body가 비어있지 않고 SuccessResponse 구조를 포함한다")
        void approveModification_responseBodyIsNotEmpty() throws Exception {
            doNothing().when(adminRequestCommandService).approveModification(anyLong(), any());

            String responseBody = mockMvc.perform(patch("/api/admin/requests/change/approve")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"changeRequestId\": 1, \"adminComment\": \"승인\"}"))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            org.assertj.core.api.Assertions.assertThat(responseBody).isNotBlank();
            org.assertj.core.api.Assertions.assertThat(responseBody).contains("status");
            org.assertj.core.api.Assertions.assertThat(responseBody).contains("message");
        }
    }

    @Nested
    @DisplayName("PATCH /api/admin/requests/change/reject")
    class RejectModification {

        @Test
        @DisplayName("변경 요청 거절 시 200 OK와 SuccessResponse 형식의 body를 반환한다")
        void rejectModification_returns200WithSuccessResponseBody() throws Exception {
            doNothing().when(adminRequestCommandService).rejectModification(anyLong(), any());

            mockMvc.perform(patch("/api/admin/requests/change/reject")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"changeRequestId\": 1, \"adminComment\": \"거절합니다.\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200))
                    .andExpect(jsonPath("$.message").exists())
                    .andExpect(jsonPath("$.data").isEmpty());
        }

        @Test
        @DisplayName("changeRequestId가 없으면 400 Bad Request를 반환한다")
        void rejectModification_returns400_whenChangeRequestIdMissing() throws Exception {
            mockMvc.perform(patch("/api/admin/requests/change/reject")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"adminComment\": \"거절합니다.\"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("adminComment가 없으면 400 Bad Request를 반환한다")
        void rejectModification_returns400_whenAdminCommentMissing() throws Exception {
            mockMvc.perform(patch("/api/admin/requests/change/reject")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"changeRequestId\": 1}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("응답 body가 비어있지 않고 SuccessResponse 구조를 포함한다")
        void rejectModification_responseBodyIsNotEmpty() throws Exception {
            doNothing().when(adminRequestCommandService).rejectModification(anyLong(), any());

            String responseBody = mockMvc.perform(patch("/api/admin/requests/change/reject")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"changeRequestId\": 1, \"adminComment\": \"거절\"}"))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            org.assertj.core.api.Assertions.assertThat(responseBody).isNotBlank();
            org.assertj.core.api.Assertions.assertThat(responseBody).contains("status");
            org.assertj.core.api.Assertions.assertThat(responseBody).contains("message");
        }
    }
}
