package DGU_AI_LAB.admin_be.domain.alarm.service;

import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import DGU_AI_LAB.admin_be.support.LogCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Slack Webhook URL은 그 자체가 인증 수단이므로 로그로 남기면 안 된다.
 */
@ExtendWith(MockitoExtension.class)
class SlackApiServiceLoggingTest {

    private static final String WEBHOOK_URL = "https://hooks.slack.com/services/T000/B000/super-secret-token";

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    private SlackApiService slackApiService;

    @BeforeEach
    void setUp() {
        slackApiService = new SlackApiService(redisTemplate);
        ReflectionTestUtils.setField(slackApiService, "botToken", "test-bot-token");
        ReflectionTestUtils.setField(slackApiService, "restTemplate", restTemplate);
    }

    @Test
    @DisplayName("Webhook 전송 실패 시 URL을 로그로 남기지 않고 예외 타입만 남긴다")
    void doesNotLogWebhookUrlOnFailure() {
        when(restTemplate.postForEntity(eq(WEBHOOK_URL), any(), eq(String.class)))
                .thenThrow(new ResourceAccessException("connect timed out"));

        try (LogCaptor logCaptor = LogCaptor.forClass(SlackApiService.class)) {
            assertThatThrownBy(() -> slackApiService.sendWebhook(WEBHOOK_URL, "메시지"))
                    .isInstanceOf(BusinessException.class);

            String logs = logCaptor.joined();
            assertThat(logs)
                    .doesNotContain(WEBHOOK_URL)
                    .doesNotContain("hooks.slack.com")
                    .doesNotContain("super-secret-token");
            assertThat(logs).contains("ResourceAccessException");
        }
    }

    @Test
    @DisplayName("Slack 사용자를 찾지 못했을 때 이메일을 로그로 남기지 않고 username만 남긴다")
    void doesNotLogEmailWhenUserNotFound() {
        String username = "no-such-user";
        String email = "victim@dgu.ac.kr";
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(any())).thenReturn(List.of());

        try (LogCaptor logCaptor = LogCaptor.forClass(SlackApiService.class)) {
            assertThatThrownBy(() -> slackApiService.sendDM(username, email, "메시지"))
                    .isInstanceOf(BusinessException.class);

            String logs = logCaptor.joined();
            assertThat(logs)
                    .doesNotContain(email)
                    .contains(username);
        }
    }
}
