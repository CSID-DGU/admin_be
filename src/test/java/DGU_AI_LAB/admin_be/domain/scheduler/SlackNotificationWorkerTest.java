package DGU_AI_LAB.admin_be.domain.scheduler;

import DGU_AI_LAB.admin_be.domain.alarm.dto.SlackMessageDto;
import DGU_AI_LAB.admin_be.domain.alarm.service.SlackApiService;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
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
class SlackNotificationWorkerTest {

    @InjectMocks
    private SlackNotificationWorker worker;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private SlackApiService slackApiService;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private ListOperations<String, Object> listOperations;

    private static final String QUEUE_KEY = "slack:notification:queue";

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForList()).thenReturn(listOperations);
    }

    @Nested
    @DisplayName("processSlackQueue")
    class ProcessSlackQueue {

        @Test
        @DisplayName("큐가 비어있으면 아무 것도 처리하지 않는다")
        void processSlackQueue_doesNothing_whenQueueEmpty() throws Exception {
            when(listOperations.leftPop(QUEUE_KEY)).thenReturn(null);

            worker.processSlackQueue();

            verify(slackApiService, never()).sendWebhook(anyString(), anyString());
            verify(slackApiService, never()).sendDM(anyString(), anyString(), anyString());
            verify(listOperations, never()).rightPush(anyString(), any());
        }

        @Test
        @DisplayName("WEBHOOK 타입 메시지는 sendWebhook으로 전송된다")
        void processSlackQueue_sendsWebhook_whenWebhookTypeMessage() throws Exception {
            SlackMessageDto dto = SlackMessageDto.builder()
                    .type(SlackMessageDto.MessageType.WEBHOOK)
                    .webhookUrl("https://hooks.slack.com/test")
                    .message("관리자 알림")
                    .build();

            when(listOperations.leftPop(QUEUE_KEY)).thenReturn(dto);
            when(objectMapper.convertValue(dto, SlackMessageDto.class)).thenReturn(dto);

            worker.processSlackQueue();

            verify(slackApiService).sendWebhook("https://hooks.slack.com/test", "관리자 알림");
            verify(slackApiService, never()).sendDM(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("DM 타입 메시지는 sendDM으로 전송된다")
        void processSlackQueue_sendsDM_whenDMTypeMessage() throws Exception {
            SlackMessageDto dto = SlackMessageDto.builder()
                    .type(SlackMessageDto.MessageType.DM)
                    .username("홍길동")
                    .email("hong@dgu.ac.kr")
                    .message("승인 알림")
                    .build();

            when(listOperations.leftPop(QUEUE_KEY)).thenReturn(dto);
            when(objectMapper.convertValue(dto, SlackMessageDto.class)).thenReturn(dto);

            worker.processSlackQueue();

            verify(slackApiService).sendDM("홍길동", "hong@dgu.ac.kr", "승인 알림");
            verify(slackApiService, never()).sendWebhook(anyString(), anyString());
        }

        @Test
        @DisplayName("일시적 네트워크 오류 발생 시 retryCount를 1 증가시켜 큐에 재적재한다")
        void processSlackQueue_requeuesWithIncrementedRetryCount_onTransientFailure() throws Exception {
            SlackMessageDto dto = SlackMessageDto.builder()
                    .type(SlackMessageDto.MessageType.WEBHOOK)
                    .webhookUrl("https://hooks.slack.com/test")
                    .message("알림")
                    .build();

            when(listOperations.leftPop(QUEUE_KEY)).thenReturn(dto);
            when(objectMapper.convertValue(dto, SlackMessageDto.class)).thenReturn(dto);
            doThrow(new RuntimeException("Slack API 일시 장애")).when(slackApiService).sendWebhook(anyString(), anyString());

            worker.processSlackQueue();

            ArgumentCaptor<SlackMessageDto> captor = ArgumentCaptor.forClass(SlackMessageDto.class);
            verify(listOperations).rightPush(eq(QUEUE_KEY), captor.capture());
            assertThat(captor.getValue().getRetryCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("재시도 2회 누적된 메시지가 실패하면 retryCount가 3이 되어 큐에 재적재된다")
        void processSlackQueue_requeuesWith3_whenRetryCount2() throws Exception {
            SlackMessageDto dto = SlackMessageDto.builder()
                    .type(SlackMessageDto.MessageType.WEBHOOK)
                    .webhookUrl("https://hooks.slack.com/test")
                    .message("알림")
                    .retryCount(2)
                    .build();

            when(listOperations.leftPop(QUEUE_KEY)).thenReturn(dto);
            when(objectMapper.convertValue(dto, SlackMessageDto.class)).thenReturn(dto);
            doThrow(new RuntimeException("오류")).when(slackApiService).sendWebhook(anyString(), anyString());

            worker.processSlackQueue();

            ArgumentCaptor<SlackMessageDto> captor = ArgumentCaptor.forClass(SlackMessageDto.class);
            verify(listOperations).rightPush(eq(QUEUE_KEY), captor.capture());
            assertThat(captor.getValue().getRetryCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("최대 재시도 횟수(3회) 초과 시 메시지를 폐기하고 큐에 재적재하지 않는다")
        void processSlackQueue_dropsMessage_whenMaxRetryExceeded() throws Exception {
            SlackMessageDto dto = SlackMessageDto.builder()
                    .type(SlackMessageDto.MessageType.WEBHOOK)
                    .webhookUrl("https://hooks.slack.com/test")
                    .message("알림")
                    .retryCount(3)
                    .build();

            when(listOperations.leftPop(QUEUE_KEY)).thenReturn(dto);
            when(objectMapper.convertValue(dto, SlackMessageDto.class)).thenReturn(dto);
            doThrow(new RuntimeException("오류")).when(slackApiService).sendWebhook(anyString(), anyString());

            worker.processSlackQueue();

            verify(listOperations, never()).rightPush(anyString(), any());
        }

        @Test
        @DisplayName("BusinessException 발생 시 재시도 없이 즉시 폐기한다 (유저 없음 등 영구 오류)")
        void processSlackQueue_dropsImmediately_onBusinessException_withoutRequeue() throws Exception {
            SlackMessageDto dto = SlackMessageDto.builder()
                    .type(SlackMessageDto.MessageType.DM)
                    .username("존재안함")
                    .email("none@dgu.ac.kr")
                    .message("알림")
                    .build();

            when(listOperations.leftPop(QUEUE_KEY)).thenReturn(dto);
            when(objectMapper.convertValue(dto, SlackMessageDto.class)).thenReturn(dto);
            doThrow(new BusinessException(ErrorCode.USER_NOT_FOUND))
                    .when(slackApiService).sendDM(anyString(), anyString(), anyString());

            worker.processSlackQueue();

            verify(listOperations, never()).rightPush(anyString(), any());
        }

        @Test
        @DisplayName("역직렬화 실패 시 메시지를 폐기하고 예외를 전파하지 않는다")
        void processSlackQueue_dropsMessage_onDeserializationFailure() {
            Object invalidPayload = "invalid-json";
            when(listOperations.leftPop(QUEUE_KEY)).thenReturn(invalidPayload);
            when(objectMapper.convertValue(invalidPayload, SlackMessageDto.class))
                    .thenThrow(new IllegalArgumentException("역직렬화 실패"));

            worker.processSlackQueue();

            verify(slackApiService, never()).sendWebhook(anyString(), anyString());
            verify(slackApiService, never()).sendDM(anyString(), anyString(), anyString());
            verify(listOperations, never()).rightPush(anyString(), any());
        }
    }
}
