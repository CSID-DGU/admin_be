package DGU_AI_LAB.admin_be.domain.alarm.service;

import DGU_AI_LAB.admin_be.global.util.MessageUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlarmServiceTest {

    @InjectMocks
    private AlarmService alarmService;

    @Mock
    private SlackApiService slackApiService;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private MessageUtils messageUtils;

    @Mock
    private ListOperations<String, Object> listOperations;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(alarmService, "notiLogWebhookUrl", "https://hooks.slack.com/noti");
        ReflectionTestUtils.setField(alarmService, "errorLogWebhookUrl", "https://hooks.slack.com/error");
        ReflectionTestUtils.setField(alarmService, "farmAdminWebhookUrl", "https://hooks.slack.com/farm");
        ReflectionTestUtils.setField(alarmService, "labAdminWebhookUrl", "https://hooks.slack.com/lab");
        ReflectionTestUtils.setField(alarmService, "from", "noreply@dgu.ac.kr");
    }

    @Nested
    @DisplayName("sendSlackAlert")
    class SendSlackAlert {

        @Test
        @DisplayName("Redis 정상 시 큐에 메시지가 적재된다")
        void sendSlackAlert_queuesMessage_whenRedisWorks() {
            when(redisTemplate.opsForList()).thenReturn(listOperations);
            when(listOperations.rightPush(anyString(), any())).thenReturn(1L);

            assertThatCode(() -> alarmService.sendSlackAlert("test message", "https://hooks.slack.com/test"))
                    .doesNotThrowAnyException();

            verify(listOperations).rightPush(anyString(), any());
        }

        @Test
        @DisplayName("Redis 장애 시 WEBHOOK 타입은 직접 전송하며 NPE가 발생하지 않는다")
        void sendSlackAlert_fallback_doesNotThrowNPE_whenRedisDown() {
            when(redisTemplate.opsForList()).thenThrow(new RuntimeException("Redis 연결 실패"));
            when(messageUtils.get("notification.error.redis-fallback")).thenReturn(" [Redis 장애]");
            doNothing().when(slackApiService).sendWebhook(anyString(), anyString());

            assertThatCode(() -> alarmService.sendSlackAlert("test message", "https://hooks.slack.com/error"))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("sendDMAlert")
    class SendDMAlert {

        @Test
        @DisplayName("Redis 정상 시 DM 큐에 메시지가 적재된다")
        void sendDMAlert_queuesMessage_whenRedisWorks() {
            when(redisTemplate.opsForList()).thenReturn(listOperations);
            when(listOperations.rightPush(anyString(), any())).thenReturn(1L);

            assertThatCode(() -> alarmService.sendDMAlert("testuser", "test@example.com", "hello"))
                    .doesNotThrowAnyException();

            verify(listOperations).rightPush(anyString(), any());
        }

        @Test
        @DisplayName("Redis 장애 시 DM 타입은 webhookUrl이 null이어도 NPE 없이 직접 전송된다")
        void sendDMAlert_fallback_doesNotThrowNPE_whenWebhookUrlIsNull() {
            when(redisTemplate.opsForList()).thenThrow(new RuntimeException("Redis 연결 실패"));
            when(messageUtils.get("notification.error.redis-fallback")).thenReturn(" [Redis 장애]");
            doNothing().when(slackApiService).sendDM(anyString(), anyString(), anyString());
            doNothing().when(slackApiService).sendWebhook(anyString(), anyString());

            // DM 타입은 webhookUrl이 null → 이전에는 NPE 발생했음
            assertThatCode(() -> alarmService.sendDMAlert("testuser", "test@example.com", "hello"))
                    .doesNotThrowAnyException();
        }
    }
}
