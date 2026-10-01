package DGU_AI_LAB.admin_be.domain.users.controller;

import DGU_AI_LAB.admin_be.domain.users.dto.response.PasswordResetSummaryDTO;
import DGU_AI_LAB.admin_be.domain.users.service.PasswordResetService;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import DGU_AI_LAB.admin_be.error.exception.EntityNotFoundException;
import DGU_AI_LAB.admin_be.support.WebMvcTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        value = AdminPasswordResetController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class}
)
class AdminPasswordResetControllerTest extends WebMvcTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PasswordResetService passwordResetService;

    private static PasswordResetSummaryDTO summary(String status) {
        return new PasswordResetSummaryDTO(12L, 5L, "홍길동", "test@dgu.ac.kr", "honggildong", status, null, null);
    }

    @Test
    @DisplayName("GET /api/admin/password-resets는 처리할 신청 목록을 돌려준다")
    void listsOpenRequests() throws Exception {
        when(passwordResetService.getOpenRequests()).thenReturn(List.of(summary("PENDING")));

        mockMvc.perform(get("/api/admin/password-resets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].passwordResetRequestId").value(12))
                .andExpect(jsonPath("$.data[0].status").value("PENDING"))
                .andExpect(jsonPath("$.data[0].email").value("test@dgu.ac.kr"));
    }

    @Test
    @DisplayName("POST /{id}/approval은 작업만 등록하므로 202로 답한다")
    void approvalIsAccepted() throws Exception {
        when(passwordResetService.approve(eq(12L), isNull())).thenReturn(summary("PROCESSING"));

        mockMvc.perform(post("/api/admin/password-resets/12/approval"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.status").value("PROCESSING"));
    }

    @Test
    @DisplayName("이미 처리된 신청을 승인하면 409")
    void approvingClosedRequestIsConflict() throws Exception {
        when(passwordResetService.approve(eq(12L), isNull()))
                .thenThrow(new BusinessException(ErrorCode.PASSWORD_RESET_ALREADY_CLOSED));

        mockMvc.perform(post("/api/admin/password-resets/12/approval")).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /{id}/rejection은 거절하고 200으로 답한다")
    void rejectionIsOk() throws Exception {
        when(passwordResetService.deny(eq(12L), isNull())).thenReturn(summary("DENIED"));

        mockMvc.perform(post("/api/admin/password-resets/12/rejection"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DENIED"));
    }

    @Test
    @DisplayName("없는 신청이면 404")
    void unknownRequestIsNotFound() throws Exception {
        when(passwordResetService.deny(eq(404L), isNull()))
                .thenThrow(new EntityNotFoundException(ErrorCode.PASSWORD_RESET_REQUEST_NOT_FOUND));

        mockMvc.perform(post("/api/admin/password-resets/404/rejection")).andExpect(status().isNotFound());
    }
}
