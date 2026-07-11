package DGU_AI_LAB.admin_be.domain.scheduler;

import DGU_AI_LAB.admin_be.domain.alarm.dto.SlackMessageDto;
import DGU_AI_LAB.admin_be.domain.alarm.service.InfraAlarmService;
import DGU_AI_LAB.admin_be.domain.alarm.service.SlackApiService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InfraSlackNotificationWorkerTest {

    @InjectMocks
    private InfraSlackNotificationWorker worker;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private SlackApiService slackApiService;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private ListOperations<String, Object> listOperations;

    private static final String INFRA_QUEUE_KEY = InfraAlarmService.INFRA_SLACK_QUEUE_KEY;
    private static final String BACKEND_QUEUE_KEY = "slack:notification:queue";

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForList()).thenReturn(listOperations);
    }

    @Nested
    @DisplayName("processInfraSlackQueue")
    class ProcessInfraSlackQueue {

        @Test
        @DisplayName("큐가 비어있으면 아무 것도 처리하지 않는다")
        void processInfraSlackQueue_doesNothing_whenQueueEmpty() throws Exception {
            when(listOperations.leftPop(INFRA_QUEUE_KEY)).thenReturn(null);

            worker.processInfraSlackQueue();

            verify(slackApiService, never()).sendWebhook(anyString(), anyString());
            verify(listOperations, never()).rightPush(anyString(), any());
        }

        @Test
        @DisplayName("정상 메시지는 올바른 webhookUrl과 message로 sendWebhook을 호출한다")
        void processInfraSlackQueue_sendsWebhookWithCorrectArgs() throws Exception {
            SlackMessageDto dto = SlackMessageDto.builder()
                    .type(SlackMessageDto.MessageType.WEBHOOK)
                    .webhookUrl("https://hooks.slack.com/infra")
                    .message("GPU 온도 경고")
                    .build();

            when(listOperations.leftPop(INFRA_QUEUE_KEY)).thenReturn(dto);
            when(objectMapper.convertValue(dto, SlackMessageDto.class)).thenReturn(dto);

            worker.processInfraSlackQueue();

            verify(slackApiService).sendWebhook("https://hooks.slack.com/infra", "GPU 온도 경고");
        }

        @Test
        @DisplayName("인프라 큐 소비 시 백엔드 큐(slack:notification:queue)는 건드리지 않는다")
        void processInfraSlackQueue_onlyConsumesInfraQueue_notBackendQueue() throws Exception {
            when(listOperations.leftPop(INFRA_QUEUE_KEY)).thenReturn(null);

            worker.processInfraSlackQueue();

            verify(listOperations, never()).leftPop(BACKEND_QUEUE_KEY);
            verify(listOperations, never()).rightPush(eq(BACKEND_QUEUE_KEY), any());
        }

        @Test
        @DisplayName("전송 실패 시 retryCount를 1 증가시켜 인프라 큐에 재적재한다")
        void processInfraSlackQueue_requeuesWithIncrementedRetryCount_onFailure() throws Exception {
            SlackMessageDto dto = SlackMessageDto.builder()
                    .type(SlackMessageDto.MessageType.WEBHOOK)
                    .webhookUrl("https://hooks.slack.com/infra")
                    .message("경고")
                    .build();

            when(listOperations.leftPop(INFRA_QUEUE_KEY)).thenReturn(dto);
            when(objectMapper.convertValue(dto, SlackMessageDto.class)).thenReturn(dto);
            doThrow(new RuntimeException("Slack API 오류")).when(slackApiService).sendWebhook(anyString(), anyString());

            worker.processInfraSlackQueue();

            ArgumentCaptor<SlackMessageDto> captor = ArgumentCaptor.forClass(SlackMessageDto.class);
            verify(listOperations).rightPush(eq(INFRA_QUEUE_KEY), captor.capture());
            assertThat(captor.getValue().getRetryCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("최대 재시도 횟수(3회) 초과 시 메시지를 폐기하고 재적재하지 않는다")
        void processInfraSlackQueue_dropsMessage_whenMaxRetryExceeded() throws Exception {
            SlackMessageDto dto = SlackMessageDto.builder()
                    .type(SlackMessageDto.MessageType.WEBHOOK)
                    .webhookUrl("https://hooks.slack.com/infra")
                    .message("경고")
                    .retryCount(3)
                    .build();

            when(listOperations.leftPop(INFRA_QUEUE_KEY)).thenReturn(dto);
            when(objectMapper.convertValue(dto, SlackMessageDto.class)).thenReturn(dto);
            doThrow(new RuntimeException("오류")).when(slackApiService).sendWebhook(anyString(), anyString());

            worker.processInfraSlackQueue();

            verify(listOperations, never()).rightPush(anyString(), any());
        }

        @Test
        @DisplayName("역직렬화 실패 시 메시지를 폐기하고 예외를 전파하지 않는다")
        void processInfraSlackQueue_dropsMessage_onDeserializationFailure() {
            Object invalidPayload = 12345;
            when(listOperations.leftPop(INFRA_QUEUE_KEY)).thenReturn(invalidPayload);
            when(objectMapper.convertValue(invalidPayload, SlackMessageDto.class))
                    .thenThrow(new IllegalArgumentException("역직렬화 실패"));

            worker.processInfraSlackQueue();

            verify(slackApiService, never()).sendWebhook(anyString(), anyString());
            verify(listOperations, never()).rightPush(anyString(), any());
        }
    }
}
