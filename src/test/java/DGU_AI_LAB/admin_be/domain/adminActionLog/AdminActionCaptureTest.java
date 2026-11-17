package DGU_AI_LAB.admin_be.domain.adminActionLog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import DGU_AI_LAB.admin_be.domain.adminActionLog.dto.AdminActionEvent;
import DGU_AI_LAB.admin_be.domain.adminActionLog.service.AdminActionLogDispatcher;
import DGU_AI_LAB.admin_be.domain.adminActionLog.web.AdminActionBodyAdvice;
import DGU_AI_LAB.admin_be.domain.adminActionLog.web.AdminActionInterceptor;
import DGU_AI_LAB.admin_be.domain.users.dto.request.AdminPasswordResetRequestDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.request.ChangeRoleRequestDTO;
import DGU_AI_LAB.admin_be.error.GlobalExceptionHandler;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.validation.Valid;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.ServletRequestAttributes;

@DisplayName("관리자 요청 기록 수집")
class AdminActionCaptureTest {
    AdminActionLogDispatcher dispatcher;
    MockMvc mvc;

    @BeforeEach
    void setup() {
        dispatcher = mock(AdminActionLogDispatcher.class);
        ObjectMapper mapper = new ObjectMapper();
        // Advice가 현재 요청에 접근하도록 Spring의 요청 프록시와 같은 역할을 수행한다.
        var request =
                mock(
                        jakarta.servlet.http.HttpServletRequest.class,
                        invocation -> {
                            var current =
                                    ((ServletRequestAttributes)
                                                    org.springframework.web.context.request
                                                            .RequestContextHolder
                                                            .currentRequestAttributes())
                                            .getRequest();
                            return invocation
                                    .getMethod()
                                    .invoke(current, invocation.getArguments());
                        });
        mvc =
                MockMvcBuilders.standaloneSetup(new Actions())
                        .setControllerAdvice(
                                new GlobalExceptionHandler(), new AdminActionBodyAdvice(request))
                        .addInterceptors(new AdminActionInterceptor(dispatcher, mapper))
                        .build();
    }

    @Test
    @DisplayName("역할 변경 값을 수집하고 검증 실패한 비밀번호 값은 제외한다")
    void capturesRoleAndOmitsPasswordIncludingInvalidPassword() throws Exception {
        mvc.perform(
                        patch("/api/admin/test/42/role")
                                .contentType("application/json")
                                .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk());
        var role = event();
        assertThat(role.requestedRole()).isEqualTo("ADMIN");
        assertThat(role.targetId()).isEqualTo("42");
        reset(dispatcher);
        mvc.perform(
                        put("/api/admin/test/42/password")
                                .contentType("application/json")
                                .content("{\"newPassword\":\"secret\"}"))
                .andExpect(status().isBadRequest());
        var password = event();
        assertThat(password.captureStatus()).isEqualTo("OMITTED_BY_POLICY");
        assertThat(password.requestValues()).doesNotContain("secret", "newPassword");
        assertThat(password.httpStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("DTO 변환이 실패해도 원문 없이 실패 기록을 남긴다")
    void malformedJsonStillRecordsFailureWithoutRawBody() throws Exception {
        mvc.perform(
                        patch("/api/admin/test/42/role")
                                .contentType("application/json")
                                .content("{\"role\":\"SECRET_BAD_ROLE\"}"))
                .andExpect(status().isBadRequest());
        var event = event();
        assertThat(event.httpStatus()).isEqualTo(400);
        assertThat(event.captureStatus()).isEqualTo("UNAVAILABLE");
        assertThat(event.requestValues()).doesNotContain("SECRET_BAD_ROLE");
    }

    @Test
    @DisplayName("처리된 서버 오류와 작업 접수의 HTTP 상태를 구분한다")
    void capturesHandledServerErrorAndAcceptedWithoutCallingThemSuccess() throws Exception {
        mvc.perform(post("/api/admin/test/42/fail")).andExpect(status().isInternalServerError());
        assertThat(event().httpStatus()).isEqualTo(500);
        reset(dispatcher);
        mvc.perform(post("/api/admin/test/42/approval")).andExpect(status().isAccepted());
        assertThat(event().httpStatus()).isEqualTo(202);
    }

    AdminActionEvent event() {
        var captor = ArgumentCaptor.forClass(AdminActionEvent.class);
        verify(dispatcher).submit(captor.capture());
        return captor.getValue();
    }

    @RestController
    @RequestMapping("/api/admin/test/{id}")
    static class Actions {
        @PatchMapping("/role")
        @AdminAction(value = AdminActionType.USER_ROLE_CHANGE, targetId = "id")
        public void role(@PathVariable Long id, @RequestBody @Valid ChangeRoleRequestDTO dto) {}

        @PutMapping("/password")
        @AdminAction(value = AdminActionType.USER_PASSWORD_RESET, targetId = "id")
        public void password(
                @PathVariable Long id, @RequestBody @Valid AdminPasswordResetRequestDTO dto) {}

        @PostMapping("/fail")
        @AdminAction(value = AdminActionType.REQUEST_REJECT, targetId = "id")
        public void fail(@PathVariable Long id) {
            throw new IllegalStateException("test failure");
        }

        @PostMapping("/approval")
        @AdminAction(value = AdminActionType.REQUEST_APPROVE, targetId = "id")
        public ResponseEntity<Void> approve(@PathVariable Long id) {
            return ResponseEntity.accepted().build();
        }
    }
}
