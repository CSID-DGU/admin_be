package DGU_AI_LAB.admin_be.domain.requests.controller;

import DGU_AI_LAB.admin_be.domain.requests.entity.ChangeType;
import DGU_AI_LAB.admin_be.domain.requests.service.RequestCommandService;
import DGU_AI_LAB.admin_be.domain.requests.service.RequestQueryService;
import DGU_AI_LAB.admin_be.support.WebMvcTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        value = MyChangeRequestController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class}
)
class MyChangeRequestControllerTest extends WebMvcTestSupport {

    private static final String REASON = "가".repeat(100);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RequestCommandService requestCommandService;

    @MockitoBean
    private RequestQueryService requestQueryService;

    private static String body(String reason) {
        return "{\"requestId\": 42, \"changeType\": \"EXPIRES_AT\", \"newValue\": \"2030-01-01T23:59:59\", \"reason\": \""
                + reason + "\"}";
    }

    @Test
    @DisplayName("POST /api/users/me/change-requests — 본문의 대상 신청으로 변경 요청을 만들고 201을 반환한다")
    void createsChangeRequest() throws Exception {
        mockMvc.perform(post("/api/users/me/change-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(REASON)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201));

        verify(requestCommandService).createSingleChangeRequest(isNull(),
                argThat(dto -> dto.requestId() == 42L && dto.changeType() == ChangeType.EXPIRES_AT && REASON.equals(dto.reason())));
    }

    @Test
    @DisplayName("사유가 100자에 못 미치면 400을 반환하고 만들지 않는다")
    void rejectsShortReason() throws Exception {
        mockMvc.perform(post("/api/users/me/change-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("가".repeat(99))))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(requestCommandService);
    }

    @Test
    @DisplayName("GET /api/users/me/change-requests — 내 변경 요청 목록을 반환한다")
    void listsMyChangeRequests() throws Exception {
        mockMvc.perform(get("/api/users/me/change-requests"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());

        verify(requestQueryService).getMyChangeRequests(isNull());
    }
}
