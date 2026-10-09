package DGU_AI_LAB.admin_be.domain.warnings.controller;

import DGU_AI_LAB.admin_be.domain.warnings.dto.WarningStatusResponseDTO;
import DGU_AI_LAB.admin_be.domain.warnings.service.WarningService;
import DGU_AI_LAB.admin_be.support.WebMvcTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 요청이 실제 스프링 컨텍스트를 거쳐 서비스에 닿는지 본다 — 파라미터 검증 선언이 잘못되면 모든 요청이 500이 된다. */
@WebMvcTest(value = {AdminWarningController.class, MyWarningController.class},
        excludeAutoConfiguration = {SecurityAutoConfiguration.class})
class AdminWarningControllerTest extends WebMvcTestSupport {

    private static final WarningStatusResponseDTO STATUS = new WarningStatusResponseDTO(1, false, 7, null, List.of());

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WarningService warningService;

    @BeforeEach
    void setUp() {
        when(warningService.getStatus(3L)).thenReturn(STATUS);
        when(warningService.grant(any(), eq(3L), any())).thenReturn(STATUS);
        when(warningService.deduct(any(), eq(3L), any())).thenReturn(STATUS);
        when(warningService.cancel(any(), eq(3L), eq(9L), any())).thenReturn(STATUS);
    }

    @Test
    @DisplayName("경고 현황 조회는 200")
    void getsWarnings() throws Exception {
        mockMvc.perform(get("/api/admin/users/3/warnings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(1));
    }

    @Test
    @DisplayName("경고 부여는 사유를 넘기고 201")
    void grantsWarning() throws Exception {
        mockMvc.perform(post("/api/admin/users/3/warnings").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"사유\"}"))
                .andExpect(status().isCreated());

        verify(warningService).grant(any(), eq(3L), eq("사유"));
    }

    @Test
    @DisplayName("경고 부여에 사유가 비어 있으면 400")
    void grantWithoutReasonIsRejected() throws Exception {
        mockMvc.perform(post("/api/admin/users/3/warnings").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\" \"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(warningService);
    }

    @Test
    @DisplayName("경고 차감은 사유를 넘기고 201")
    void deductsWarning() throws Exception {
        mockMvc.perform(post("/api/admin/users/3/warning-deductions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"사유\"}"))
                .andExpect(status().isCreated());

        verify(warningService).deduct(any(), eq(3L), eq("사유"));
    }

    @Test
    @DisplayName("경고 취소는 쿼리의 사유를 넘기고 200")
    void cancelsWarning() throws Exception {
        mockMvc.perform(delete("/api/admin/users/3/warnings/9").param("reason", "잘못 준 경고"))
                .andExpect(status().isOk());

        verify(warningService).cancel(any(), eq(3L), eq(9L), eq("잘못 준 경고"));
    }

    @Test
    @DisplayName("경고 취소에 사유가 없거나 500자를 넘으면 400")
    void cancelWithBadReasonIsRejected() throws Exception {
        mockMvc.perform(delete("/api/admin/users/3/warnings/9"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(delete("/api/admin/users/3/warnings/9").param("reason", "가".repeat(501)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(warningService);
    }

    @Test
    @DisplayName("본인 경고 현황 조회는 200")
    void getsOwnWarnings() throws Exception {
        when(warningService.getOwnStatus(any())).thenReturn(STATUS);

        mockMvc.perform(get("/api/users/me/warnings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(1));
    }
}
