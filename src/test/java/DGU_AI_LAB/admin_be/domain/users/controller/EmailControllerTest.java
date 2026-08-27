package DGU_AI_LAB.admin_be.domain.users.controller;

import DGU_AI_LAB.admin_be.global.util.EmailService;
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

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        value = EmailController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class}
)
class EmailControllerTest extends WebMvcTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EmailService emailService;

    @Nested
    @DisplayName("POST /api/auth/email/send")
    class SendCode {

        @Test
        @DisplayName("요청 본문의 이메일로 인증번호를 발송한다")
        void sendCode_readsEmailFromRequestBody() throws Exception {
            mockMvc.perform(post("/api/auth/email/send")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"test@dgu.ac.kr\"}"))
                    .andExpect(status().isOk());

            verify(emailService).sendEmailVerificationCode("test@dgu.ac.kr");
        }

        @Test
        @DisplayName("이메일을 쿼리 파라미터로만 보내면 400을 반환한다")
        void sendCode_rejectsQueryParameterOnlyRequest() throws Exception {
            mockMvc.perform(post("/api/auth/email/send")
                            .param("email", "test@dgu.ac.kr")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());

            verify(emailService, never()).sendEmailVerificationCode(anyString());
        }

        @Test
        @DisplayName("이메일 형식이 아니면 400을 반환한다")
        void sendCode_rejectsMalformedEmail() throws Exception {
            mockMvc.perform(post("/api/auth/email/send")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"not-an-email\"}"))
                    .andExpect(status().isBadRequest());

            verify(emailService, never()).sendEmailVerificationCode(anyString());
        }
    }

    @Nested
    @DisplayName("POST /api/auth/email/verify")
    class VerifyCode {

        @Test
        @DisplayName("요청 본문의 이메일과 인증번호로 검증한다")
        void verifyCode_readsEmailAndCodeFromRequestBody() throws Exception {
            mockMvc.perform(post("/api/auth/email/verify")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"test@dgu.ac.kr\",\"code\":\"123456\"}"))
                    .andExpect(status().isOk());

            verify(emailService).confirmAuthCode("test@dgu.ac.kr", "123456");
        }

        @Test
        @DisplayName("인증번호가 비어 있으면 400을 반환한다")
        void verifyCode_rejectsBlankCode() throws Exception {
            mockMvc.perform(post("/api/auth/email/verify")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"test@dgu.ac.kr\",\"code\":\"\"}"))
                    .andExpect(status().isBadRequest());

            verify(emailService, never()).confirmAuthCode(anyString(), anyString());
        }
    }
}
