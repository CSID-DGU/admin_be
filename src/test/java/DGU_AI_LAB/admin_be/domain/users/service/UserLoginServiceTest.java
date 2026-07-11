package DGU_AI_LAB.admin_be.domain.users.service;

import DGU_AI_LAB.admin_be.domain.users.dto.request.UserLoginRequestDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.request.UserRegisterRequestDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.response.UserTokenResponseDTO;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import DGU_AI_LAB.admin_be.error.exception.UnauthorizedException;
import DGU_AI_LAB.admin_be.global.auth.jwt.JwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserLoginServiceTest {

    @InjectMocks
    private UserLoginService userLoginService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private User activeUser;

    @BeforeEach
    void setUp() {
        activeUser = User.builder()
                .email("test@dgu.ac.kr")
                .password("encodedPassword")
                .name("홍길동")
                .studentId("2021001234")
                .phone("010-1234-5678")
                .department("컴퓨터공학과")
                .build();
    }

    @Nested
    @DisplayName("register (회원가입)")
    class Register {

        @Test
        @DisplayName("이메일 인증이 완료되고 중복이 없으면 회원가입에 성공한다")
        void register_success() {
            when(redisTemplate.hasKey("VERIFIED:test@dgu.ac.kr")).thenReturn(true);
            when(userRepository.findByEmail("test@dgu.ac.kr")).thenReturn(Optional.empty());
            when(passwordEncoder.encode(anyString())).thenReturn("encodedPw");

            UserRegisterRequestDTO dto = new UserRegisterRequestDTO(
                    "test@dgu.ac.kr", "password123", "홍길동", "컴퓨터공학과", "2021001234", "010-1234-5678"
            );

            userLoginService.register(dto);

            verify(userRepository, times(1)).save(any(User.class));
            verify(redisTemplate, times(1)).delete("VERIFIED:test@dgu.ac.kr");
        }

        @Test
        @DisplayName("이메일 인증이 안 된 경우 UnauthorizedException을 던진다")
        void register_throwsException_whenEmailNotVerified() {
            when(redisTemplate.hasKey("VERIFIED:test@dgu.ac.kr")).thenReturn(false);

            UserRegisterRequestDTO dto = new UserRegisterRequestDTO(
                    "test@dgu.ac.kr", "password123", "홍길동", "컴퓨터공학과", "2021001234", "010-1234-5678"
            );

            assertThatThrownBy(() -> userLoginService.register(dto))
                    .isInstanceOf(UnauthorizedException.class);
        }

        @Test
        @DisplayName("이미 가입된 이메일로 회원가입하면 BusinessException을 던진다")
        void register_throwsException_whenEmailAlreadyExists() {
            when(redisTemplate.hasKey("VERIFIED:test@dgu.ac.kr")).thenReturn(true);
            when(userRepository.findByEmail("test@dgu.ac.kr")).thenReturn(Optional.of(activeUser));

            UserRegisterRequestDTO dto = new UserRegisterRequestDTO(
                    "test@dgu.ac.kr", "password123", "홍길동", "컴퓨터공학과", "2021001234", "010-1234-5678"
            );

            assertThatThrownBy(() -> userLoginService.register(dto))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    @DisplayName("login (로그인)")
    class Login {

        @Test
        @DisplayName("올바른 이메일과 비밀번호로 로그인하면 토큰을 반환하고 BCrypt 검증은 정확히 1회 실행된다")
        void login_success() {
            when(userRepository.findByEmail("test@dgu.ac.kr")).thenReturn(Optional.of(activeUser));
            when(passwordEncoder.matches("password123", "encodedPassword")).thenReturn(true);
            when(jwtProvider.getIssueToken(any(), eq(true))).thenReturn("accessToken");
            when(jwtProvider.getIssueToken(any(), eq(false))).thenReturn("refreshToken");
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            UserLoginRequestDTO dto = new UserLoginRequestDTO("test@dgu.ac.kr", "password123");
            UserTokenResponseDTO result = userLoginService.login(dto);

            assertThat(result).isNotNull();
            assertThat(result.accessToken()).isEqualTo("accessToken");
            assertThat(result.refreshToken()).isEqualTo("refreshToken");
            verify(passwordEncoder, times(1)).matches("password123", "encodedPassword");
        }

        @Test
        @DisplayName("존재하지 않는 이메일로 로그인하면 UnauthorizedException을 던진다")
        void login_throwsException_whenEmailNotFound() {
            when(userRepository.findByEmail("notexist@dgu.ac.kr")).thenReturn(Optional.empty());

            UserLoginRequestDTO dto = new UserLoginRequestDTO("notexist@dgu.ac.kr", "password");

            assertThatThrownBy(() -> userLoginService.login(dto))
                    .isInstanceOf(UnauthorizedException.class);
        }

        @Test
        @DisplayName("비활성화된 계정으로 로그인하면 UnauthorizedException을 던진다")
        void login_throwsException_whenAccountDisabled() {
            User inactiveUser = User.builder()
                    .email("inactive@dgu.ac.kr")
                    .password("encodedPassword")
                    .name("비활성유저")
                    .studentId("2021000001")
                    .phone("010-0000-0000")
                    .department("컴퓨터공학과")
                    .build();
            inactiveUser.withdraw(); // isActive = false

            when(userRepository.findByEmail("inactive@dgu.ac.kr")).thenReturn(Optional.of(inactiveUser));

            UserLoginRequestDTO dto = new UserLoginRequestDTO("inactive@dgu.ac.kr", "password");

            assertThatThrownBy(() -> userLoginService.login(dto))
                    .isInstanceOf(UnauthorizedException.class);
        }

        @Test
        @DisplayName("비밀번호가 틀리면 UnauthorizedException을 던지고 BCrypt 검증은 정확히 1회 실행된다")
        void login_throwsException_whenPasswordWrong() {
            when(userRepository.findByEmail("test@dgu.ac.kr")).thenReturn(Optional.of(activeUser));
            when(passwordEncoder.matches("wrongPw", "encodedPassword")).thenReturn(false);

            UserLoginRequestDTO dto = new UserLoginRequestDTO("test@dgu.ac.kr", "wrongPw");

            assertThatThrownBy(() -> userLoginService.login(dto))
                    .isInstanceOf(UnauthorizedException.class);
            verify(passwordEncoder, times(1)).matches("wrongPw", "encodedPassword");
        }

        @Test
        @DisplayName("BCrypt 비밀번호 검증은 정확히 1회만 호출되어야 한다 (C-3 중복 호출 방지)")
        void login_callsPasswordMatchesExactlyOnce() {
            when(userRepository.findByEmail("test@dgu.ac.kr")).thenReturn(Optional.of(activeUser));
            when(passwordEncoder.matches("password123", "encodedPassword")).thenReturn(true);
            when(jwtProvider.getIssueToken(any(), eq(true))).thenReturn("accessToken");
            when(jwtProvider.getIssueToken(any(), eq(false))).thenReturn("refreshToken");
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            UserLoginRequestDTO dto = new UserLoginRequestDTO("test@dgu.ac.kr", "password123");
            userLoginService.login(dto);

            // 중복 호출 버그(C-3) 수정 검증: 동일 인자로 2번 호출되면 안 됨
            verify(passwordEncoder, times(1)).matches("password123", "encodedPassword");
        }
    }
}
