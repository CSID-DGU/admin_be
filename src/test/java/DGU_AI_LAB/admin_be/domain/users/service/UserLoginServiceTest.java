package DGU_AI_LAB.admin_be.domain.users.service;

import DGU_AI_LAB.admin_be.domain.users.dto.request.UserLoginRequestDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.request.UserRegisterRequestDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.response.UserTokenResponseDTO;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import org.springframework.dao.DataIntegrityViolationException;
import DGU_AI_LAB.admin_be.error.exception.UnauthorizedException;
import DGU_AI_LAB.admin_be.global.auth.jwt.JwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

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
        // @Value 필드는 Mockito가 주입하지 않으므로 직접 설정 (604800000ms = 7일)
        ReflectionTestUtils.setField(userLoginService, "REFRESH_TOKEN_EXPIRE_TIME", 604800000L);

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
                    "test@dgu.ac.kr", "password123", "홍길동", "컴퓨터공학과", "2021001234", "010-1234-5678", "honggildong"
            );

            userLoginService.register(dto);

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository, times(1)).saveAndFlush(captor.capture());
            // 가입 시 우분투 유저네임만 정해지고, 리눅스 계정(UID/GID)은 첫 승인 때 만들어진다.
            assertThat(captor.getValue().getUbuntuUsername()).isEqualTo("honggildong");
            assertThat(captor.getValue().hasUbuntuAccount()).isFalse();
            verify(redisTemplate, times(1)).delete("VERIFIED:test@dgu.ac.kr");
        }

        @Test
        @DisplayName("이미 사용 중인 우분투 유저네임으로 가입하면 DUPLICATE_USERNAME을 던진다")
        void register_throwsException_whenUbuntuUsernameTaken() {
            when(redisTemplate.hasKey("VERIFIED:test@dgu.ac.kr")).thenReturn(true);
            when(userRepository.findByEmail("test@dgu.ac.kr")).thenReturn(Optional.empty());
            when(userRepository.existsByUbuntuUsername("honggildong")).thenReturn(true);

            UserRegisterRequestDTO dto = new UserRegisterRequestDTO(
                    "test@dgu.ac.kr", "password123", "홍길동", "컴퓨터공학과", "2021001234", "010-1234-5678", "honggildong"
            );

            assertThatThrownBy(() -> userLoginService.register(dto))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DUPLICATE_USERNAME);

            verify(userRepository, never()).saveAndFlush(any(User.class));
        }

        @Test
        @DisplayName("사전 검사 통과 후 우분투 유저네임 unique 제약에 걸리면 DUPLICATE_USERNAME(409)으로 변환한다 — 이메일 중복과 구분해야 사용자가 뭘 고칠지 안다")
        void register_mapsUbuntuUsernameViolationToDuplicateUsername() {
            when(redisTemplate.hasKey("VERIFIED:test@dgu.ac.kr")).thenReturn(true);
            when(userRepository.findByEmail("test@dgu.ac.kr")).thenReturn(Optional.empty());
            when(passwordEncoder.encode(anyString())).thenReturn("encodedPw");
            when(userRepository.saveAndFlush(any(User.class)))
                    .thenThrow(new DataIntegrityViolationException("constraint violation",
                            new RuntimeException("Duplicate entry 'honggildong' for key 'uk_users_ubuntu_username'")));

            UserRegisterRequestDTO dto = new UserRegisterRequestDTO(
                    "test@dgu.ac.kr", "password123", "홍길동", "컴퓨터공학과", "2021001234", "010-1234-5678", "honggildong"
            );

            assertThatThrownBy(() -> userLoginService.register(dto))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DUPLICATE_USERNAME);

            verify(redisTemplate, never()).delete("VERIFIED:test@dgu.ac.kr");
        }

        @Test
        @DisplayName("중복 검사 통과 후 email unique 제약에 걸리면 500이 아니라 USER_ALREADY_EXISTS(409)로 변환한다")
        void register_mapsUniqueViolationToAlreadyExists() {
            when(redisTemplate.hasKey("VERIFIED:test@dgu.ac.kr")).thenReturn(true);
            // 사전 검사는 통과한다 — 그 직후 같은 이메일로 다른 가입이 먼저 커밋된 상황
            when(userRepository.findByEmail("test@dgu.ac.kr")).thenReturn(Optional.empty());
            when(passwordEncoder.encode(anyString())).thenReturn("encodedPw");
            when(userRepository.saveAndFlush(any(User.class)))
                    .thenThrow(new DataIntegrityViolationException("uk_users_email"));

            UserRegisterRequestDTO dto = new UserRegisterRequestDTO(
                    "test@dgu.ac.kr", "password123", "홍길동", "컴퓨터공학과", "2021001234", "010-1234-5678", "honggildong"
            );

            assertThatThrownBy(() -> userLoginService.register(dto))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_ALREADY_EXISTS);

            // 가입이 실패했으므로 이메일 인증 키를 소비하면 안 된다 (재시도 가능해야 한다)
            verify(redisTemplate, never()).delete("VERIFIED:test@dgu.ac.kr");
        }

        @Test
        @DisplayName("이메일 인증이 안 된 경우 UnauthorizedException을 던진다")
        void register_throwsException_whenEmailNotVerified() {
            when(redisTemplate.hasKey("VERIFIED:test@dgu.ac.kr")).thenReturn(false);

            UserRegisterRequestDTO dto = new UserRegisterRequestDTO(
                    "test@dgu.ac.kr", "password123", "홍길동", "컴퓨터공학과", "2021001234", "010-1234-5678", "honggildong"
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
                    "test@dgu.ac.kr", "password123", "홍길동", "컴퓨터공학과", "2021001234", "010-1234-5678", "honggildong"
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
            verify(valueOperations).set(anyString(), eq("refreshToken"), eq(604800000L), eq(TimeUnit.MILLISECONDS));
        }

        @Test
        @DisplayName("존재하지 않는 이메일로 로그인하면 UnauthorizedException을 던진다")
        void login_throwsException_whenEmailNotFound() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(userRepository.findByEmail("notexist@dgu.ac.kr")).thenReturn(Optional.empty());

            UserLoginRequestDTO dto = new UserLoginRequestDTO("notexist@dgu.ac.kr", "password");

            assertThatThrownBy(() -> userLoginService.login(dto))
                    .isInstanceOf(UnauthorizedException.class);
            verify(valueOperations).increment("LOGIN_FAIL:notexist@dgu.ac.kr");
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

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(userRepository.findByEmail("inactive@dgu.ac.kr")).thenReturn(Optional.of(inactiveUser));

            UserLoginRequestDTO dto = new UserLoginRequestDTO("inactive@dgu.ac.kr", "password");

            assertThatThrownBy(() -> userLoginService.login(dto))
                    .isInstanceOf(UnauthorizedException.class);
        }

        @Test
        @DisplayName("비밀번호가 틀리면 UnauthorizedException을 던지고 BCrypt 검증은 정확히 1회 실행된다")
        void login_throwsException_whenPasswordWrong() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(userRepository.findByEmail("test@dgu.ac.kr")).thenReturn(Optional.of(activeUser));
            when(passwordEncoder.matches("wrongPw", "encodedPassword")).thenReturn(false);

            UserLoginRequestDTO dto = new UserLoginRequestDTO("test@dgu.ac.kr", "wrongPw");

            assertThatThrownBy(() -> userLoginService.login(dto))
                    .isInstanceOf(UnauthorizedException.class);
            verify(passwordEncoder, times(1)).matches("wrongPw", "encodedPassword");
            verify(valueOperations).increment("LOGIN_FAIL:test@dgu.ac.kr");
        }

        @Test
        @DisplayName("15분 내 5회 실패하면 이후 로그인 시도는 비밀번호 확인 없이 잠긴다")
        void login_locksOut_afterMaxFailedAttempts() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("LOGIN_FAIL:test@dgu.ac.kr")).thenReturn("5");

            UserLoginRequestDTO dto = new UserLoginRequestDTO("test@dgu.ac.kr", "password123");

            assertThatThrownBy(() -> userLoginService.login(dto))
                    .isInstanceOf(BusinessException.class);
            verifyNoInteractions(passwordEncoder);
            verify(userRepository, never()).findByEmail(anyString());
        }

        @Test
        @DisplayName("실패 횟수가 임계값 미만이면 잠기지 않고 정상적으로 비밀번호를 검증한다")
        void login_doesNotLockOut_whenAttemptsBelowThreshold() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("LOGIN_FAIL:test@dgu.ac.kr")).thenReturn("4");
            when(userRepository.findByEmail("test@dgu.ac.kr")).thenReturn(Optional.of(activeUser));
            when(passwordEncoder.matches("password123", "encodedPassword")).thenReturn(true);
            when(jwtProvider.getIssueToken(any(), eq(true))).thenReturn("accessToken");
            when(jwtProvider.getIssueToken(any(), eq(false))).thenReturn("refreshToken");

            UserLoginRequestDTO dto = new UserLoginRequestDTO("test@dgu.ac.kr", "password123");
            UserTokenResponseDTO result = userLoginService.login(dto);

            assertThat(result).isNotNull();
            verify(passwordEncoder, times(1)).matches("password123", "encodedPassword");
        }

        @Test
        @DisplayName("실패 카운터가 처음 1로 증가할 때만 TTL을 설정한다")
        void login_setsExpireOnlyOnFirstFailure() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.increment("LOGIN_FAIL:test@dgu.ac.kr")).thenReturn(1L);
            when(userRepository.findByEmail("test@dgu.ac.kr")).thenReturn(Optional.of(activeUser));
            when(passwordEncoder.matches("wrongPw", "encodedPassword")).thenReturn(false);

            UserLoginRequestDTO dto = new UserLoginRequestDTO("test@dgu.ac.kr", "wrongPw");

            assertThatThrownBy(() -> userLoginService.login(dto))
                    .isInstanceOf(UnauthorizedException.class);
            verify(redisTemplate).expire(eq("LOGIN_FAIL:test@dgu.ac.kr"), eq(900L), eq(TimeUnit.SECONDS));
        }

        @Test
        @DisplayName("실패 카운터가 이미 1보다 크게 증가하면 TTL을 다시 설정하지 않는다")
        void login_doesNotResetExpire_onSubsequentFailures() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.increment("LOGIN_FAIL:test@dgu.ac.kr")).thenReturn(2L);
            when(userRepository.findByEmail("test@dgu.ac.kr")).thenReturn(Optional.of(activeUser));
            when(passwordEncoder.matches("wrongPw", "encodedPassword")).thenReturn(false);

            UserLoginRequestDTO dto = new UserLoginRequestDTO("test@dgu.ac.kr", "wrongPw");

            assertThatThrownBy(() -> userLoginService.login(dto))
                    .isInstanceOf(UnauthorizedException.class);
            verify(redisTemplate, never()).expire(anyString(), anyLong(), any(TimeUnit.class));
        }

        @Test
        @DisplayName("로그인 성공 시 실패 카운터를 삭제한다")
        void login_success_clearsFailedAttemptCounter() {
            when(userRepository.findByEmail("test@dgu.ac.kr")).thenReturn(Optional.of(activeUser));
            when(passwordEncoder.matches("password123", "encodedPassword")).thenReturn(true);
            when(jwtProvider.getIssueToken(any(), eq(true))).thenReturn("accessToken");
            when(jwtProvider.getIssueToken(any(), eq(false))).thenReturn("refreshToken");
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            UserLoginRequestDTO dto = new UserLoginRequestDTO("test@dgu.ac.kr", "password123");
            userLoginService.login(dto);

            verify(redisTemplate).delete("LOGIN_FAIL:test@dgu.ac.kr");
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
