package DGU_AI_LAB.admin_be.domain.alarm.service;

import DGU_AI_LAB.admin_be.domain.alarm.dto.SlackMessageDto;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.global.util.MessageUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AlarmServiceTest {

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
    private ListOperations<String, Object> listOperations;

    private static final String QUEUE_KEY = "slack:notification:queue";
    private static final String NOTI_WEBHOOK = "https://hooks.slack.com/noti";
    private static final String ERROR_WEBHOOK = "https://hooks.slack.com/error";
    private static final String FARM_WEBHOOK = "https://hooks.slack.com/farm";
    private static final String LAB_WEBHOOK = "https://hooks.slack.com/lab";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(alarmService, "notiLogWebhookUrl", NOTI_WEBHOOK);
        ReflectionTestUtils.setField(alarmService, "errorLogWebhookUrl", ERROR_WEBHOOK);
        ReflectionTestUtils.setField(alarmService, "farmAdminWebhookUrl", FARM_WEBHOOK);
        ReflectionTestUtils.setField(alarmService, "labAdminWebhookUrl", LAB_WEBHOOK);
        ReflectionTestUtils.setField(alarmService, "from", "no-reply@dgu.ac.kr");
        when(redisTemplate.opsForList()).thenReturn(listOperations);
    }

    @Nested
    @DisplayName("sendSlackAlert")
    class SendSlackAlert {

        @Test
        @DisplayName("명시적 webhookUrl을 제공하면 해당 URL로 큐에 적재한다")
        void sendSlackAlert_usesProvidedWebhookUrl() {
            String webhookUrl = "https://hooks.slack.com/custom";

            alarmService.sendSlackAlert("테스트 메시지", webhookUrl);

            ArgumentCaptor<SlackMessageDto> captor = ArgumentCaptor.forClass(SlackMessageDto.class);
            verify(listOperations).rightPush(eq(QUEUE_KEY), captor.capture());
            assertThat(captor.getValue().getWebhookUrl()).isEqualTo(webhookUrl);
            assertThat(captor.getValue().getType()).isEqualTo(SlackMessageDto.MessageType.WEBHOOK);
        }

        @Test
        @DisplayName("webhookUrl이 null이면 errorLog 채널로 폴백된다")
        void sendSlackAlert_usesErrorLogUrl_whenWebhookUrlNull() {
            alarmService.sendSlackAlert("에러 메시지", null);

            ArgumentCaptor<SlackMessageDto> captor = ArgumentCaptor.forClass(SlackMessageDto.class);
            verify(listOperations).rightPush(eq(QUEUE_KEY), captor.capture());
            assertThat(captor.getValue().getWebhookUrl()).isEqualTo(ERROR_WEBHOOK);
        }

        @Test
        @DisplayName("webhookUrl이 빈 문자열이면 errorLog 채널로 폴백된다")
        void sendSlackAlert_usesErrorLogUrl_whenWebhookUrlEmpty() {
            alarmService.sendSlackAlert("에러 메시지", "");

            ArgumentCaptor<SlackMessageDto> captor = ArgumentCaptor.forClass(SlackMessageDto.class);
            verify(listOperations).rightPush(eq(QUEUE_KEY), captor.capture());
            assertThat(captor.getValue().getWebhookUrl()).isEqualTo(ERROR_WEBHOOK);
        }

        @Test
        @DisplayName("Redis 장애 시 SlackApiService.sendWebhook으로 직접 전송을 시도한다")
        void sendSlackAlert_fallbackToDirectSend_whenRedisDown() throws Exception {
            when(redisTemplate.opsForList()).thenThrow(new RuntimeException("Redis 연결 실패"));
            when(messageUtils.get(anyString())).thenReturn("(fallback)");

            alarmService.sendSlackAlert("긴급 메시지", FARM_WEBHOOK);

            verify(slackApiService).sendWebhook(eq(FARM_WEBHOOK), anyString());
        }
    }

    @Nested
    @DisplayName("sendDMAlert")
    class SendDMAlert {

        @Test
        @DisplayName("DM 알림은 DM 타입으로 큐에 적재된다")
        void sendDMAlert_pushesToQueueWithDMType() {
            alarmService.sendDMAlert("홍길동", "hong@dgu.ac.kr", "승인 완료");

            ArgumentCaptor<SlackMessageDto> captor = ArgumentCaptor.forClass(SlackMessageDto.class);
            verify(listOperations).rightPush(eq(QUEUE_KEY), captor.capture());
            SlackMessageDto dto = captor.getValue();
            assertThat(dto.getType()).isEqualTo(SlackMessageDto.MessageType.DM);
            assertThat(dto.getUsername()).isEqualTo("홍길동");
            assertThat(dto.getEmail()).isEqualTo("hong@dgu.ac.kr");
            assertThat(dto.getMessage()).isEqualTo("승인 완료");
        }
    }

    @Nested
    @DisplayName("sendNewRequestNotification — 서버별 채널 라우팅")
    class SendNewRequestNotification {

        @Test
        @DisplayName("FARM 서버 신청은 farm 관리자 채널로 적재된다")
        void sendNewRequestNotification_routesToFarmWebhook_forFarmServer() {
            Request request = mockRequest("홍길동", "FARM");
            when(messageUtils.get(anyString(), any(), any())).thenReturn("새 신청");

            alarmService.sendNewRequestNotification(request);

            ArgumentCaptor<SlackMessageDto> captor = ArgumentCaptor.forClass(SlackMessageDto.class);
            verify(listOperations).rightPush(eq(QUEUE_KEY), captor.capture());
            assertThat(captor.getValue().getWebhookUrl()).isEqualTo(FARM_WEBHOOK);
        }

        @Test
        @DisplayName("LAB 서버 신청은 lab 관리자 채널로 적재된다")
        void sendNewRequestNotification_routesToLabWebhook_forLabServer() {
            Request request = mockRequest("이순신", "LAB");
            when(messageUtils.get(anyString(), any(), any())).thenReturn("새 신청");

            alarmService.sendNewRequestNotification(request);

            ArgumentCaptor<SlackMessageDto> captor = ArgumentCaptor.forClass(SlackMessageDto.class);
            verify(listOperations).rightPush(eq(QUEUE_KEY), captor.capture());
            assertThat(captor.getValue().getWebhookUrl()).isEqualTo(LAB_WEBHOOK);
        }

        @Test
        @DisplayName("알 수 없는 서버 신청은 errorLog 채널로 폴백된다")
        void sendNewRequestNotification_fallsBackToErrorLog_forUnknownServer() {
            Request request = mockRequest("김철수", "UNKNOWN_SERVER");
            when(messageUtils.get(anyString(), any(), any())).thenReturn("새 신청");

            alarmService.sendNewRequestNotification(request);

            ArgumentCaptor<SlackMessageDto> captor = ArgumentCaptor.forClass(SlackMessageDto.class);
            verify(listOperations).rightPush(eq(QUEUE_KEY), captor.capture());
            assertThat(captor.getValue().getWebhookUrl()).isEqualTo(ERROR_WEBHOOK);
        }
    }

    @Nested
    @DisplayName("sendApprovalNotification")
    class SendApprovalNotification {

        @Test
        @DisplayName("승인 알림은 메일 전송과 DM/모니터링 큐 적재를 모두 수행한다")
        void sendApprovalNotification_callsMailAndQueuePush() {
            Request request = mockRequest("박지성", "FARM");
            User user = request.getUser();
            when(user.getEmail()).thenReturn("park@dgu.ac.kr");
            when(messageUtils.get(eq("notification.approval.subject"))).thenReturn("승인 완료");
            when(messageUtils.get(eq("notification.approval.body"), any())).thenReturn("승인되었습니다");
            when(messageUtils.get(eq("notification.monitor.log"), any(), any(), any())).thenReturn("로그");

            alarmService.sendApprovalNotification(request);

            // 메일 전송 1회
            verify(mailSender).send(any(SimpleMailMessage.class));
            // DM 큐 적재 1회 + 모니터링 WEBHOOK 큐 적재 1회 = 총 2회
            verify(listOperations, times(2)).rightPush(eq(QUEUE_KEY), any());
        }
    }

    @Nested
    @DisplayName("sendAdminSlackNotification")
    class SendAdminSlackNotification {

        @Test
        @DisplayName("FARM 서버명으로 호출하면 farm 관리자 채널로 적재된다")
        void sendAdminSlackNotification_routesToFarmChannel() {
            alarmService.sendAdminSlackNotification("FARM", "Pod 삭제 완료");

            ArgumentCaptor<SlackMessageDto> captor = ArgumentCaptor.forClass(SlackMessageDto.class);
            verify(listOperations).rightPush(eq(QUEUE_KEY), captor.capture());
            assertThat(captor.getValue().getWebhookUrl()).isEqualTo(FARM_WEBHOOK);
            assertThat(captor.getValue().getMessage()).isEqualTo("Pod 삭제 완료");
        }

        @Test
        @DisplayName("LAB 서버명으로 호출하면 lab 관리자 채널로 적재된다")
        void sendAdminSlackNotification_routesToLabChannel() {
            alarmService.sendAdminSlackNotification("LAB", "계정 삭제 완료");

            ArgumentCaptor<SlackMessageDto> captor = ArgumentCaptor.forClass(SlackMessageDto.class);
            verify(listOperations).rightPush(eq(QUEUE_KEY), captor.capture());
            assertThat(captor.getValue().getWebhookUrl()).isEqualTo(LAB_WEBHOOK);
        }
    }

    @Nested
    @DisplayName("sendAllAlerts")
    class SendAllAlerts {

        @Test
        @DisplayName("메일, DM 큐, 모니터링 로그 큐가 모두 호출된다")
        void sendAllAlerts_callsMailDmAndMonitoringLog() {
            when(messageUtils.get(anyString(), any(), any(), any())).thenReturn("모니터링 로그");

            alarmService.sendAllAlerts("홍길동", "hong@dgu.ac.kr", "제목", "내용");

            verify(mailSender).send(any(SimpleMailMessage.class));
            // DM 1회 + 모니터링 WEBHOOK 1회
            verify(listOperations, times(2)).rightPush(eq(QUEUE_KEY), any());
        }

        @Test
        @DisplayName("메일 전송 실패해도 DM 큐 적재는 계속 진행된다")
        void sendAllAlerts_continuesWithDM_whenMailFails() {
            doThrow(new RuntimeException("SMTP 오류")).when(mailSender).send(any(SimpleMailMessage.class));
            when(messageUtils.get(anyString(), any(), any(), any())).thenReturn("로그");

            alarmService.sendAllAlerts("홍길동", "hong@dgu.ac.kr", "제목", "내용");

            // 메일은 실패했지만 큐 적재(DM + 모니터링)는 계속 진행
            verify(listOperations, atLeastOnce()).rightPush(eq(QUEUE_KEY), any());
        }
    }

    private Request mockRequest(String userName, String serverName) {
        User user = mock(User.class);
        when(user.getName()).thenReturn(userName);
        ResourceGroup rg = mock(ResourceGroup.class);
        when(rg.getServerName()).thenReturn(serverName);
        Request request = mock(Request.class);
        when(request.getUser()).thenReturn(user);
        when(request.getResourceGroup()).thenReturn(rg);
        return request;
    }
}
