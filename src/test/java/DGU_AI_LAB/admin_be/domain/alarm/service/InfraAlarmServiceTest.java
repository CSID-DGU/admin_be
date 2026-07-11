package DGU_AI_LAB.admin_be.domain.alarm.service;

import DGU_AI_LAB.admin_be.domain.alarm.dto.SlackMessageDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InfraAlarmServiceTest {

    @InjectMocks
    private InfraAlarmService infraAlarmService;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private SlackApiService slackApiService;

    @Mock
    private ListOperations<String, Object> listOperations;

    @Nested
    @DisplayName("enqueue")
    class Enqueue {

        @Test
        @DisplayName("정상 상황에서 Redis 인프라 큐에 메시지를 적재한다")
        void enqueue_pushesToInfraQueue() {
            when(redisTemplate.opsForList()).thenReturn(listOperations);

            infraAlarmService.enqueue("https://hooks.slack.com/test", "테스트 메시지");

            ArgumentCaptor<SlackMessageDto> captor = ArgumentCaptor.forClass(SlackMessageDto.class);
            verify(listOperations).rightPush(
                    eq(InfraAlarmService.INFRA_SLACK_QUEUE_KEY),
                    captor.capture()
            );
            SlackMessageDto queued = captor.getValue();
            assertThat(queued.getType()).isEqualTo(SlackMessageDto.MessageType.WEBHOOK);
            assertThat(queued.getWebhookUrl()).isEqualTo("https://hooks.slack.com/test");
            assertThat(queued.getMessage()).isEqualTo("테스트 메시지");
            assertThat(queued.getRetryCount()).isZero();
        }

        @Test
        @DisplayName("인프라 큐는 백엔드 알림 큐와 다른 키를 사용한다")
        void enqueue_usesInfraQueueKey_notBackendQueueKey() {
            when(redisTemplate.opsForList()).thenReturn(listOperations);

            infraAlarmService.enqueue("https://hooks.slack.com/test", "메시지");

            verify(listOperations).rightPush(
                    eq("slack:infra:notification:queue"), any()
            );
            verify(listOperations, never()).rightPush(
                    eq("slack:notification:queue"), any()
            );
        }

        @Test
        @DisplayName("Redis 장애 시 Webhook으로 직접 전송한다 (fallback)")
        void enqueue_fallbackToDirectSend_whenRedisDown() throws Exception {
            when(redisTemplate.opsForList()).thenThrow(new RuntimeException("Redis 연결 실패"));

            infraAlarmService.enqueue("https://hooks.slack.com/test", "긴급 메시지");

            verify(slackApiService).sendWebhook("https://hooks.slack.com/test", "긴급 메시지");
        }

        @Test
        @DisplayName("Redis 장애 + Fallback도 실패해도 예외가 밖으로 전파되지 않는다")
        void enqueue_doesNotThrow_whenBothRedisAndFallbackFail() throws Exception {
            when(redisTemplate.opsForList()).thenThrow(new RuntimeException("Redis 실패"));
            doThrow(new RuntimeException("Slack API 실패")).when(slackApiService).sendWebhook(any(), any());

            // 예외가 전파되지 않아야 함
            infraAlarmService.enqueue("https://hooks.slack.com/test", "메시지");
        }
    }
}
