package DGU_AI_LAB.admin_be.global.util;

import DGU_AI_LAB.admin_be.support.LogCaptor;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 이메일 인증번호는 계정 생성 권한과 직결되므로 로그에 평문으로 남기면 안 된다.
 */
@ExtendWith(MockitoExtension.class)
class EmailServiceLoggingTest {

    private static final String EMAIL = "victim@dgu.ac.kr";

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
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
    }

    @Test
    @DisplayName("인증번호 발송 시 인증번호와 이메일 주소가 로그에 남지 않는다")
    void doesNotLogAuthCode() {
        try (LogCaptor logCaptor = LogCaptor.forClass(EmailService.class)) {
            emailService.sendEmailVerificationCode(EMAIL);

            ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
            verify(valueOperations).set(anyString(), codeCaptor.capture(), anyLong(), eq(TimeUnit.SECONDS));
            String issuedCode = codeCaptor.getValue();

            assertThat(issuedCode).hasSize(6);
            assertThat(logCaptor.joined())
                    .doesNotContain(issuedCode)
                    .doesNotContain(EMAIL);
        }
    }
}
