package DGU_AI_LAB.admin_be.global.util;

import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import jakarta.mail.internet.MimeMessage;
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
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @InjectMocks
    private EmailService emailService;

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private MimeMessage mimeMessage;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Nested
    @DisplayName("sendEmailVerificationCode")
    class SendEmailVerificationCode {

        @Test
        @DisplayName("이메일 인증번호 발송 성공 시 Redis에 인증코드가 저장된다")
        void sendEmailVerificationCode_success() {
            when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
            doNothing().when(mailSender).send(any(MimeMessage.class));

            assertThatCode(() -> emailService.sendEmailVerificationCode("test@example.com"))
                    .doesNotThrowAnyException();

            verify(valueOperations).set(anyString(), anyString(), anyLong(), any());
        }
    }

    @Nested
    @DisplayName("confirmAuthCode")
    class ConfirmAuthCode {

        @Test
        @DisplayName("올바른 인증 코드 입력 시 인증 성공 처리된다")
        void confirmAuthCode_success() {
            when(valueOperations.get(anyString())).thenReturn("123456");

            assertThatCode(() -> emailService.confirmAuthCode("test@example.com", "123456"))
                    .doesNotThrowAnyException();

            verify(redisTemplate).delete(anyString());
            verify(valueOperations).set(startsWith("VERIFIED:"), eq("true"), anyLong(), any());
        }

        @Test
        @DisplayName("잘못된 인증 코드 입력 시 BusinessException(INVALID_AUTH_CODE) 발생")
        void confirmAuthCode_wrongCode_throwsBusinessException() {
            when(valueOperations.get(anyString())).thenReturn("123456");

            assertThatThrownBy(() -> emailService.confirmAuthCode("test@example.com", "000000"))
                    .isInstanceOf(BusinessException.class);
        }
    }
}
