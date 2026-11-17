package DGU_AI_LAB.admin_be.domain.adminActionLog;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import DGU_AI_LAB.admin_be.domain.adminActionLog.controller.AdminActionLogController;
import DGU_AI_LAB.admin_be.domain.adminActionLog.dto.AdminActionEvent;
import DGU_AI_LAB.admin_be.domain.adminActionLog.entity.AdminActionLog;
import DGU_AI_LAB.admin_be.domain.adminActionLog.repository.AdminActionLogRepository;
import DGU_AI_LAB.admin_be.domain.adminActionLog.service.AdminActionLogQueryService;
import DGU_AI_LAB.admin_be.error.GlobalExceptionHandler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.UUID;

@DataJpaTest
@DisplayName("관리자 작업 기록 조회")
class AdminActionLogQueryTest {
    @Autowired EntityManager em;
    @Autowired AdminActionLogRepository repository;
    MockMvc mvc;
    ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setup() {
        var service = new AdminActionLogQueryService(em, repository, mapper);
        mvc =
                MockMvcBuilders.standaloneSetup(new AdminActionLogController(service))
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();
    }

    @Test
    @DisplayName("조회 API로 승인자와 관리자별 기간 작업 및 역할 변경자를 확인한다")
    void answersAllThreeRequiredQuestionsThroughApi() throws Exception {
        save(7L, AdminActionType.REQUEST_APPROVE, "5", 202, null);
        save(7L, AdminActionType.USER_ROLE_CHANGE, "42", 200, "ADMIN");
        save(8L, AdminActionType.USER_ROLE_CHANGE, "42", 400, "ADMIN");
        mvc.perform(
                        get("/api/admin/action-logs")
                                .param("targetType", "REQUEST")
                                .param("targetId", "5")
                                .param("actionType", "REQUEST_APPROVE")
                                .param("httpStatus", "202"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].adminId").value(7))
                .andExpect(jsonPath("$.data.items[0].occurredAt").exists())
                .andExpect(jsonPath("$.data.items[0].result").value("ACCEPTED"));
        mvc.perform(
                        get("/api/admin/action-logs")
                                .param("adminId", "7")
                                .param("from", "2026-11-09T00:00:00Z")
                                .param("to", "2026-11-16T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2));
        mvc.perform(
                        get("/api/admin/action-logs")
                                .param("targetType", "USER")
                                .param("targetId", "42")
                                .param("actionType", "USER_ROLE_CHANGE")
                                .param("requestedRole", "ADMIN")
                                .param("httpStatus", "200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].adminId").value(7));
    }

    @Test
    @DisplayName("같은 시각의 기록도 커서로 구분하고 잘못된 조회 입력은 거절한다")
    void cursorHandlesEqualTimestampsAndRejectsInvalidInput() throws Exception {
        save(7L, AdminActionType.REQUEST_APPROVE, "5", 202, null);
        save(7L, AdminActionType.REQUEST_APPROVE, "6", 202, null);
        String first =
                mvc.perform(get("/api/admin/action-logs").param("size", "1"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.hasNext").value(true))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String cursor = mapper.readTree(first).at("/data/nextCursor").asText();
        mvc.perform(get("/api/admin/action-logs").param("size", "1").param("cursor", cursor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.data.items[0].targetId").value("5"));
        mvc.perform(get("/api/admin/action-logs").param("cursor", "invalid"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/action-logs").param("size", "101"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("상세는 안전한 요청 값을 제공하고 수정·삭제 API는 제공하지 않는다")
    void detailsExposeSafeJsonAndNoMutationApiExists() throws Exception {
        var log = save(7L, AdminActionType.REQUEST_APPROVE, "5", 202, null);
        mvc.perform(get("/api/admin/action-logs/" + log.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requestValues.imageId").value(3));
        mvc.perform(delete("/api/admin/action-logs/" + log.getId()))
                .andExpect(status().isMethodNotAllowed());
        mvc.perform(patch("/api/admin/action-logs/" + log.getId()))
                .andExpect(status().isMethodNotAllowed());
    }

    AdminActionLog save(
            Long admin, AdminActionType action, String target, int status, String role) {
        return repository.saveAndFlush(
                AdminActionLog.from(
                        new AdminActionEvent(
                                UUID.randomUUID().toString(),
                                LocalDateTime.of(2026, 11, 10, 12, 0),
                                admin,
                                action,
                                action.targetType(),
                                target,
                                status,
                                role,
                                "CAPTURED",
                                "{\"imageId\":3}")));
    }
}
