package DGU_AI_LAB.admin_be.global.util;

import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import jakarta.mail.internet.MimeMessage;
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
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
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
        @DisplayName("이메일 인증번호 발송 성공 시 email:verify: prefix가 포함된 키로 Redis에 저장된다")
        void sendEmailVerificationCode_usesEmailVerifyPrefix() {
            when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
            doNothing().when(mailSender).send(any(MimeMessage.class));

            emailService.sendEmailVerificationCode("test@example.com");

            ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
            verify(valueOperations).set(keyCaptor.capture(), anyString(), anyLong(), any());

            String capturedKey = keyCaptor.getValue();
            assertThat(capturedKey).startsWith("email:verify:");
            assertThat(capturedKey).isEqualTo("email:verify:test@example.com");
        }

        @Test
        @DisplayName("이메일 인증번호 발송 성공 시 정상 처리된다")
        void sendEmailVerificationCode_success() {
            when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
            doNothing().when(mailSender).send(any(MimeMessage.class));

            assertThatCode(() -> emailService.sendEmailVerificationCode("test@example.com"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("메일 전송 실패 시 BusinessException이 발생한다")
        void sendEmailVerificationCode_throwsBusinessException_whenMailFails() {
            when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
            doThrow(new RuntimeException("SMTP 오류")).when(mailSender).send(any(MimeMessage.class));

            assertThatThrownBy(() -> emailService.sendEmailVerificationCode("test@example.com"))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    @DisplayName("confirmAuthCode")
    class ConfirmAuthCode {

        @Test
        @DisplayName("올바른 인증 코드 입력 시 email:verify: prefix 키가 삭제되고 VERIFIED: 키가 저장된다")
        void confirmAuthCode_success_usesEmailVerifyPrefixForDelete() {
            when(valueOperations.get("email:verify:test@example.com")).thenReturn("123456");

            assertThatCode(() -> emailService.confirmAuthCode("test@example.com", "123456"))
                    .doesNotThrowAnyException();

            verify(redisTemplate).delete("email:verify:test@example.com");
            verify(valueOperations).set(eq("VERIFIED:test@example.com"), eq("true"), anyLong(), any());
        }

        @Test
        @DisplayName("잘못된 인증 코드 입력 시 BusinessException 발생")
        void confirmAuthCode_wrongCode_throwsBusinessException() {
            when(valueOperations.get("email:verify:test@example.com")).thenReturn("123456");

            assertThatThrownBy(() -> emailService.confirmAuthCode("test@example.com", "000000"))
                    .isInstanceOf(BusinessException.class);
        }
    }
}
