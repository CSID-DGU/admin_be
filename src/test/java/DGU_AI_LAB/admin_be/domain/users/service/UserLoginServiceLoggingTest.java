package DGU_AI_LAB.admin_be.domain.users.service;

import DGU_AI_LAB.admin_be.domain.users.dto.request.UserRegisterRequestDTO;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import DGU_AI_LAB.admin_be.global.auth.jwt.JwtProvider;
import DGU_AI_LAB.admin_be.support.LogCaptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 회원가입 완료 로그에 가입자 이메일이 남지 않는지 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class UserLoginServiceLoggingTest {

    private static final String EMAIL = "newbie@dgu.ac.kr";

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

    @Test
    @DisplayName("회원가입 완료 로그에 이메일 주소가 남지 않는다")
    void doesNotLogEmailOnRegister() {
        UserRegisterRequestDTO request = new UserRegisterRequestDTO(
                EMAIL, "rawPassword1!", "김신입", "컴퓨터공학과", "2024001234", "010-9999-8888");

        when(redisTemplate.hasKey("VERIFIED:" + EMAIL)).thenReturn(true);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");
        when(userRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        try (LogCaptor logCaptor = LogCaptor.forClass(UserLoginService.class)) {
            userLoginService.register(request);

            assertThat(logCaptor.joined()).doesNotContain(EMAIL);
            assertThat(logCaptor.messages()).isNotEmpty();
        }
    }
}
