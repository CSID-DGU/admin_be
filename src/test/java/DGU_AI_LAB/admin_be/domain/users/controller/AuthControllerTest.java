package DGU_AI_LAB.admin_be.domain.users.controller;

import DGU_AI_LAB.admin_be.domain.users.dto.request.UserLoginRequestDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.request.UserRegisterRequestDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.response.UserTokenResponseDTO;
import DGU_AI_LAB.admin_be.domain.users.service.UserLoginService;
import DGU_AI_LAB.admin_be.domain.users.service.UserService;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.support.WebMvcTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        value = AuthController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class}
)
class AuthControllerTest extends WebMvcTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserLoginService userLoginService;

    @MockitoBean
    private UserService userService;

    @Nested
    @DisplayName("POST /api/auth/register")
    class Register {

        @Test
        @DisplayName("유효한 요청으로 회원가입하면 201 Created를 반환한다")
        void register_returns200_whenSuccess() throws Exception {
            doNothing().when(userLoginService).register(any());

            UserRegisterRequestDTO dto = new UserRegisterRequestDTO(
                    "test@dgu.ac.kr", "password123", "홍길동", "컴퓨터공학과", "2021001234", "010-1234-5678"
            );

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("이메일 형식이 잘못되면 400 Bad Request를 반환한다")
        void register_returns400_whenEmailInvalid() throws Exception {
            UserRegisterRequestDTO dto = new UserRegisterRequestDTO(
                    "invalid-email", "password123", "홍길동", "컴퓨터공학과", "2021001234", "010-1234-5678"
            );

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("이미 가입된 이메일이면 409 Conflict를 반환한다")
        void register_returns409_whenEmailAlreadyExists() throws Exception {
            doThrow(new BusinessException(ErrorCode.USER_ALREADY_EXISTS))
                    .when(userLoginService).register(any());

            UserRegisterRequestDTO dto = new UserRegisterRequestDTO(
                    "test@dgu.ac.kr", "password123", "홍길동", "컴퓨터공학과", "2021001234", "010-1234-5678"
            );

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isConflict());
        }
    }

    @Nested
    @DisplayName("POST /api/auth/login")
    class Login {

        @Test
        @DisplayName("유효한 자격증명으로 로그인하면 200 OK와 토큰을 반환한다")
        void login_returns200WithTokens_whenSuccess() throws Exception {
            UserTokenResponseDTO tokenDto = new UserTokenResponseDTO("accessToken", "refreshToken");
            when(userLoginService.login(any())).thenReturn(tokenDto);

            UserLoginRequestDTO dto = new UserLoginRequestDTO("test@dgu.ac.kr", "password123");

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").value("accessToken"))
                    .andExpect(jsonPath("$.refreshToken").value("refreshToken"));
        }

        @Test
        @DisplayName("이메일이 없으면 400 Bad Request를 반환한다")
        void login_returns400_whenEmailBlank() throws Exception {
            UserLoginRequestDTO dto = new UserLoginRequestDTO("", "password123");

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("로그인 시도 횟수를 초과하면 429 Too Many Requests를 반환한다")
        void login_returns429_whenTooManyAttempts() throws Exception {
            when(userLoginService.login(any()))
                    .thenThrow(new BusinessException(ErrorCode.TOO_MANY_LOGIN_ATTEMPTS));

            UserLoginRequestDTO dto = new UserLoginRequestDTO("test@dgu.ac.kr", "wrongPw");

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.message").value(ErrorCode.TOO_MANY_LOGIN_ATTEMPTS.getMessage()));
        }
    }
}
