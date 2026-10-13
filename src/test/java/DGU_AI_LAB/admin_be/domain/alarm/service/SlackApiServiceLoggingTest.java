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
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.ConnectException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;

/**
 * Slack Webhook URL은 그 자체가 인증 수단이므로 로그로 남기면 안 된다.
 */
@ExtendWith(MockitoExtension.class)
class SlackApiServiceLoggingTest {

    private static final String WEBHOOK_URL = "https://hooks.slack.com/services/T000/B000/super-secret-token";

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    private MockRestServiceServer server;
    private SlackApiService slackApiService;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        slackApiService = new SlackApiService(redisTemplate, builder.build());
        ReflectionTestUtils.setField(slackApiService, "botToken", "test-bot-token");
    }

    @Test
    @DisplayName("Webhook 전송 실패 시 URL을 로그로 남기지 않고 예외 타입만 남긴다")
    void doesNotLogWebhookUrlOnFailure() {
        // 연결 실패를 그대로 일으켜 RestClient가 실제로 만드는 예외를 받는다 — 그 메시지에는 요청 URL이 박혀 있다
        // ("I/O error on POST request for \"<url>\": ..."). 손으로 만든 가짜 예외로는 이 위험을 재현하지 못한다.
        server.expect(requestTo(WEBHOOK_URL)).andRespond(withException(new ConnectException("Connection refused")));

        try (LogCaptor logCaptor = LogCaptor.forClass(SlackApiService.class)) {
            assertThatThrownBy(() -> slackApiService.sendWebhook(WEBHOOK_URL, "메시지"))
                    .isInstanceOf(BusinessException.class);

            String logs = logCaptor.joined();
            assertThat(logs)
                    .doesNotContain(WEBHOOK_URL)
                    .doesNotContain("hooks.slack.com")
                    .doesNotContain("super-secret-token");
            assertThat(logs).contains("ResourceAccessException").contains("Connection refused");
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
            assertThatThrownBy(() -> slackApiService.sendDM(username, List.of(email), "메시지"))
                    .isInstanceOf(BusinessException.class);

            String logs = logCaptor.joined();
            assertThat(logs)
                    .doesNotContain(email)
                    .contains(username);
        }
    }
}
