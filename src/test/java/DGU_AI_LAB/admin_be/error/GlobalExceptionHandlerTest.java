package DGU_AI_LAB.admin_be.error;

import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import DGU_AI_LAB.admin_be.error.exception.UnauthorizedException;
import DGU_AI_LAB.admin_be.support.WebMvcTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        value = GlobalExceptionHandlerTest.TestController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class}
)
@Import(GlobalExceptionHandler.class)
class GlobalExceptionHandlerTest extends WebMvcTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @RestController
    static class TestController {
        @GetMapping("/test/unauthorized")
        public void throwUnauthorized() {
            throw new UnauthorizedException();
        }

        @GetMapping("/test/business")
        public void throwBusiness() {
            throw new BusinessException(ErrorCode.ENTITY_NOT_FOUND);
        }

        @GetMapping("/test/conflict")
        public void throwConflict() {
            throw new DataIntegrityViolationException("duplicate");
        }
    }

    @Nested
    @DisplayName("handleUnauthorizedException")
    class HandleUnauthorizedException {

        @Test
        @DisplayName("UnauthorizedException 발생 시 401과 ErrorResponse 형식으로 응답한다")
        void handleUnauthorizedException_returnsErrorResponseFormat() throws Exception {
            mockMvc.perform(get("/test/unauthorized").contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").exists())
                    .andExpect(jsonPath("$.message").exists())
                    .andExpect(jsonPath("$.isAuthenticated").doesNotExist());
        }
    }

    @Nested
    @DisplayName("handleBusinessException")
    class HandleBusinessException {

        @Test
        @DisplayName("BusinessException 발생 시 적절한 HTTP 상태와 ErrorResponse로 응답한다")
        void handleBusinessException_returnsErrorResponse() throws Exception {
            mockMvc.perform(get("/test/business").contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.message").exists());
        }
    }

    @Nested
    @DisplayName("handleDataIntegrityViolationException")
    class HandleDataIntegrityViolationException {

        @Test
        @DisplayName("DataIntegrityViolationException 발생 시 409와 ErrorResponse로 응답한다")
        void handleDataIntegrityViolationException_returnsConflict() throws Exception {
            mockMvc.perform(get("/test/conflict").contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.message").exists());
        }
    }
}
