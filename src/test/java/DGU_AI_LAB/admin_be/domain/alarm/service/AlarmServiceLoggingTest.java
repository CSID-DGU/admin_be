package DGU_AI_LAB.admin_be.domain.alarm.service;

import DGU_AI_LAB.admin_be.domain.pod.repository.PodExternalPortRepository;
import DGU_AI_LAB.admin_be.global.util.MessageUtils;
import DGU_AI_LAB.admin_be.support.LogCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

/**
 * 메일 발송 실패 로그에 수신자 이메일 전체가 그대로 남으면 안 된다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AlarmServiceLoggingTest {

    private static final String EMAIL = "victim@dgu.ac.kr";

    @InjectMocks
    private AlarmService alarmService;

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private SlackApiService slackApiService;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private MessageUtils messageUtils;

    @Mock
    private PodExternalPortRepository podExternalPortRepository;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(alarmService, "from", "noreply@dgu.ac.kr");
        ReflectionTestUtils.setField(alarmService, "errorLogWebhookUrl", "https://hooks.slack.com/services/error");
        doThrow(new RuntimeException("SMTP 연결 실패")).when(mailSender).send(any(org.springframework.mail.SimpleMailMessage.class));
    }

    @Test
    @DisplayName("메일 전송 실패 시 로그와 Slack 알림 메시지에 수신자 이메일 전체가 남지 않는다")
    void doesNotLogFullEmailOnMailSendFailure() {
        try (LogCaptor logCaptor = LogCaptor.forClass(AlarmService.class)) {
            alarmService.sendMailAlert(EMAIL, "제목", "본문");

            String logs = logCaptor.joined();
            assertThat(logs).doesNotContain(EMAIL);
        }
    }
}
