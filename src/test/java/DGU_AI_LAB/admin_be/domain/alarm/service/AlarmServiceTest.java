package DGU_AI_LAB.admin_be.domain.alarm.service;

import DGU_AI_LAB.admin_be.domain.alarm.dto.SlackMessageDto;
import DGU_AI_LAB.admin_be.domain.containerImage.entity.ContainerImage;
import DGU_AI_LAB.admin_be.domain.pod.entity.PodExternalPort;
import DGU_AI_LAB.admin_be.domain.pod.repository.PodExternalPortRepository;
import DGU_AI_LAB.admin_be.domain.requests.entity.ChangeRequest;
import DGU_AI_LAB.admin_be.domain.requests.entity.ChangeType;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.global.util.MessageUtils;
import org.springframework.mail.SimpleMailMessage;
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
import static org.assertj.core.api.Assertions.assertThatCode;
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
    private PodExternalPortRepository podExternalPortRepository;

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
        ReflectionTestUtils.setField(alarmService, "from", "noreply@dgu.ac.kr");
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

        @Test
        @DisplayName("Redis 장애 시 DM 타입은 webhookUrl이 null이어도 NPE 없이 직접 전송된다")
        void sendDMAlert_fallback_doesNotThrowNPE_whenWebhookUrlIsNull() {
            when(redisTemplate.opsForList()).thenThrow(new RuntimeException("Redis 연결 실패"));
            when(messageUtils.get("notification.error.redis-fallback")).thenReturn(" [Redis 장애]");
            doNothing().when(slackApiService).sendDM(anyString(), anyString(), anyString());
            doNothing().when(slackApiService).sendWebhook(anyString(), anyString());

            assertThatCode(() -> alarmService.sendDMAlert("testuser", "test@example.com", "hello"))
                    .doesNotThrowAnyException();
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

    @Nested
    @DisplayName("sendContainerCreatedEmail")
    class SendContainerCreatedEmail {

        @Test
        @DisplayName("추가 포트가 없을 때 '없음'으로 메일이 발송된다")
        void sendContainerCreatedEmail_sendsMailWithNoExtraPorts() {
            Request request = mockRequestForCreated("홍길동", "hong@dgu.ac.kr", "LAB", 1L);
            when(podExternalPortRepository.findByRequestRequestId(1L)).thenReturn(List.of());
            when(messageUtils.get(anyString(), any())).thenReturn("[DGU AILab] 서버 배정 안내 (LAB)");
            when(messageUtils.get(anyString(), any(), any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn("배정 안내 본문 (추가 포트: 없음)");

            alarmService.sendContainerCreatedEmail(request, "9100", "9104");

            ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
            verify(mailSender).send(captor.capture());
            assertThat(captor.getValue().getTo()).containsExactly("hong@dgu.ac.kr");
        }

        @Test
        @DisplayName("추가 포트가 있을 때 포트 목록이 포함되어 발송된다")
        void sendContainerCreatedEmail_sendsMailWithExtraPorts() {
            Request request = mockRequestForCreated("이순신", "lee@dgu.ac.kr", "FARM", 2L);
            PodExternalPort sshPort = mockPodPort("ssh", 9100);
            PodExternalPort jupyterPort = mockPodPort("jupyter", 9104);
            PodExternalPort tensorPort = mockPodPort("tensorboard", 9200);
            when(podExternalPortRepository.findByRequestRequestId(2L))
                    .thenReturn(List.of(sshPort, jupyterPort, tensorPort));
            when(messageUtils.get(anyString(), any())).thenReturn("[DGU AILab] 서버 배정 안내 (FARM)");
            when(messageUtils.get(anyString(), any(), any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn("배정 안내 본문 (추가 포트: tensorboard(9200))");

            alarmService.sendContainerCreatedEmail(request, "9100", "9104");

            // ssh, jupyter 필터링 후 tensorboard만 {7}로 전달되는지 검증
            verify(messageUtils).get(eq("email.container.created.body"),
                    any(), any(), any(), eq("9100"), eq("9104"), any(), any(), eq("tensorboard(9200)"));
        }

        @Test
        @DisplayName("ssh와 jupyter는 추가 포트에서 제외된다")
        void sendContainerCreatedEmail_excludesSshAndJupyterFromExtraPorts() {
            Request request = mockRequestForCreated("김철수", "kim@dgu.ac.kr", "LAB", 3L);
            PodExternalPort sshPort = mockPodPort("ssh", 9100);
            PodExternalPort jupyterPort = mockPodPort("jupyter", 9104);
            when(podExternalPortRepository.findByRequestRequestId(3L))
                    .thenReturn(List.of(sshPort, jupyterPort));
            when(messageUtils.get(anyString(), any())).thenReturn("제목");
            when(messageUtils.get(anyString(), any(), any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn("본문");

            alarmService.sendContainerCreatedEmail(request, "9100", "9104");

            verify(messageUtils).get(eq("email.container.created.body"),
                    any(), any(), any(), any(), any(), any(), any(), eq("없음"));
        }

        @Test
        @DisplayName("메일 발송 후 noti 채널에 모니터링 로그가 적재된다")
        void sendContainerCreatedEmail_pushesMonitoringLogToNotiChannel() {
            Request request = mockRequestForCreated("홍길동", "hong@dgu.ac.kr", "LAB", 4L);
            when(podExternalPortRepository.findByRequestRequestId(4L)).thenReturn(List.of());
            when(messageUtils.get(anyString(), any())).thenReturn("제목");
            when(messageUtils.get(anyString(), any(), any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn("본문");
            when(messageUtils.get(anyString(), any(), any(), any())).thenReturn("로그");

            alarmService.sendContainerCreatedEmail(request, "9100", "9104");

            ArgumentCaptor<SlackMessageDto> captor = ArgumentCaptor.forClass(SlackMessageDto.class);
            verify(listOperations).rightPush(eq(QUEUE_KEY), captor.capture());
            assertThat(captor.getValue().getWebhookUrl()).isEqualTo(NOTI_WEBHOOK);
        }

        @Test
        @DisplayName("메일 전송 실패 시 에러 Slack 알림이 발송된다")
        void sendContainerCreatedEmail_sendsSlackError_whenMailFails() {
            Request request = mockRequestForCreated("홍길동", "hong@dgu.ac.kr", "LAB", 5L);
            when(podExternalPortRepository.findByRequestRequestId(5L)).thenReturn(List.of());
            when(messageUtils.get(anyString(), any())).thenReturn("제목");
            when(messageUtils.get(anyString(), any(), any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn("본문");
            when(messageUtils.get(anyString(), any(), any(), any())).thenReturn("로그");
            doThrow(new RuntimeException("SMTP 오류")).when(mailSender).send(any(SimpleMailMessage.class));

            alarmService.sendContainerCreatedEmail(request, "9100", "9104");

            ArgumentCaptor<SlackMessageDto> captor = ArgumentCaptor.forClass(SlackMessageDto.class);
            verify(listOperations, atLeastOnce()).rightPush(eq(QUEUE_KEY), captor.capture());
            assertThat(captor.getAllValues()).anyMatch(dto ->
                    dto.getWebhookUrl().equals(ERROR_WEBHOOK));
        }
    }

    @Nested
    @DisplayName("sendRequestRejectedEmail")
    class SendRequestRejectedEmail {

        @Test
        @DisplayName("사용자에게 거절 이메일이 발송된다")
        void sendRequestRejectedEmail_sendsMailToUser() {
            Request request = mockRequest("홍길동", "hong@dgu.ac.kr", "FARM");
            when(messageUtils.get(anyString(), any())).thenReturn("[DGU AILab] 서버 신청 거절 안내 (FARM)");
            when(messageUtils.get(anyString(), any(), any(), any())).thenReturn("거절 안내 본문");

            alarmService.sendRequestRejectedEmail(request, "신청서 양식 미흡");

            ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
            verify(mailSender).send(captor.capture());
            assertThat(captor.getValue().getTo()).containsExactly("hong@dgu.ac.kr");
            assertThat(captor.getValue().getSubject()).isEqualTo("[DGU AILab] 서버 신청 거절 안내 (FARM)");
        }

        @Test
        @DisplayName("거절 이메일 발송 후 noti 채널에 모니터링 로그가 적재된다")
        void sendRequestRejectedEmail_pushesMonitoringLogToNotiChannel() {
            Request request = mockRequest("홍길동", "hong@dgu.ac.kr", "FARM");
            when(messageUtils.get(anyString(), any())).thenReturn("제목");
            when(messageUtils.get(anyString(), any(), any(), any())).thenReturn("로그");

            alarmService.sendRequestRejectedEmail(request, "신청서 양식 미흡");

            ArgumentCaptor<SlackMessageDto> captor = ArgumentCaptor.forClass(SlackMessageDto.class);
            verify(listOperations).rightPush(eq(QUEUE_KEY), captor.capture());
            assertThat(captor.getValue().getWebhookUrl()).isEqualTo(NOTI_WEBHOOK);
            assertThat(captor.getValue().getType()).isEqualTo(SlackMessageDto.MessageType.WEBHOOK);
        }

        @Test
        @DisplayName("메일 전송 실패 시 에러 Slack 알림이 발송된다")
        void sendRequestRejectedEmail_sendsSlackError_whenMailFails() {
            Request request = mockRequest("홍길동", "hong@dgu.ac.kr", "FARM");
            when(messageUtils.get(anyString(), any())).thenReturn("제목");
            when(messageUtils.get(anyString(), any(), any(), any())).thenReturn("로그");
            doThrow(new RuntimeException("SMTP 오류")).when(mailSender).send(any(SimpleMailMessage.class));

            alarmService.sendRequestRejectedEmail(request, "신청서 양식 미흡");

            ArgumentCaptor<SlackMessageDto> captor = ArgumentCaptor.forClass(SlackMessageDto.class);
            verify(listOperations, atLeastOnce()).rightPush(eq(QUEUE_KEY), captor.capture());
            assertThat(captor.getAllValues()).anyMatch(dto ->
                    dto.getWebhookUrl().equals(ERROR_WEBHOOK));
        }
    }

    @Nested
    @DisplayName("sendModificationRejectedEmail")
    class SendModificationRejectedEmail {

        @Test
        @DisplayName("사용자에게 변경 요청 거절 이메일이 발송된다")
        void sendModificationRejectedEmail_sendsMailToUser() {
            ChangeRequest changeRequest = mockChangeRequest("이순신", "lee@dgu.ac.kr", ChangeType.EXPIRES_AT);
            when(messageUtils.get(anyString(), any())).thenReturn("[DGU AILab] 서버 변경 요청 거절 안내 (EXPIRES_AT)");
            when(messageUtils.get(anyString(), any(), any(), any())).thenReturn("변경 거절 안내 본문");

            alarmService.sendModificationRejectedEmail(changeRequest, "변경 사유 불충분");

            ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
            verify(mailSender).send(captor.capture());
            assertThat(captor.getValue().getTo()).containsExactly("lee@dgu.ac.kr");
            assertThat(captor.getValue().getSubject()).isEqualTo("[DGU AILab] 서버 변경 요청 거절 안내 (EXPIRES_AT)");
        }

        @Test
        @DisplayName("변경 요청 거절 이메일 발송 후 noti 채널에 모니터링 로그가 적재된다")
        void sendModificationRejectedEmail_pushesMonitoringLogToNotiChannel() {
            ChangeRequest changeRequest = mockChangeRequest("이순신", "lee@dgu.ac.kr", ChangeType.EXPIRES_AT);
            when(messageUtils.get(anyString(), any())).thenReturn("제목");
            when(messageUtils.get(anyString(), any(), any(), any())).thenReturn("로그");

            alarmService.sendModificationRejectedEmail(changeRequest, "변경 사유 불충분");

            ArgumentCaptor<SlackMessageDto> captor = ArgumentCaptor.forClass(SlackMessageDto.class);
            verify(listOperations).rightPush(eq(QUEUE_KEY), captor.capture());
            assertThat(captor.getValue().getWebhookUrl()).isEqualTo(NOTI_WEBHOOK);
        }

        @Test
        @DisplayName("변경 유형이 이메일 제목에 포함된다")
        void sendModificationRejectedEmail_includesChangeTypeInSubject() {
            ChangeRequest changeRequest = mockChangeRequest("김철수", "kim@dgu.ac.kr", ChangeType.VOLUME_SIZE);
            when(messageUtils.get(eq("email.modification.rejected.subject"), eq("VOLUME_SIZE")))
                    .thenReturn("[DGU AILab] 서버 변경 요청 거절 안내 (VOLUME_SIZE)");
            when(messageUtils.get(anyString(), any(), any(), any())).thenReturn("본문");

            alarmService.sendModificationRejectedEmail(changeRequest, "볼륨 변경 불가");

            ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
            verify(mailSender).send(captor.capture());
            assertThat(captor.getValue().getSubject()).contains("VOLUME_SIZE");
        }

        @Test
        @DisplayName("메일 전송 실패 시 에러 Slack 알림이 발송된다")
        void sendModificationRejectedEmail_sendsSlackError_whenMailFails() {
            ChangeRequest changeRequest = mockChangeRequest("이순신", "lee@dgu.ac.kr", ChangeType.EXPIRES_AT);
            when(messageUtils.get(anyString(), any())).thenReturn("제목");
            when(messageUtils.get(anyString(), any(), any(), any())).thenReturn("로그");
            doThrow(new RuntimeException("SMTP 오류")).when(mailSender).send(any(SimpleMailMessage.class));

            alarmService.sendModificationRejectedEmail(changeRequest, "변경 사유 불충분");

            ArgumentCaptor<SlackMessageDto> captor = ArgumentCaptor.forClass(SlackMessageDto.class);
            verify(listOperations, atLeastOnce()).rightPush(eq(QUEUE_KEY), captor.capture());
            assertThat(captor.getAllValues()).anyMatch(dto ->
                    dto.getWebhookUrl().equals(ERROR_WEBHOOK));
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

    private Request mockRequest(String userName, String email, String serverName) {
        User user = mock(User.class);
        when(user.getName()).thenReturn(userName);
        when(user.getEmail()).thenReturn(email);
        ResourceGroup rg = mock(ResourceGroup.class);
        when(rg.getServerName()).thenReturn(serverName);
        Request request = mock(Request.class);
        when(request.getUser()).thenReturn(user);
        when(request.getResourceGroup()).thenReturn(rg);
        return request;
    }

    private ChangeRequest mockChangeRequest(String userName, String email, ChangeType changeType) {
        User user = mock(User.class);
        when(user.getName()).thenReturn(userName);
        when(user.getEmail()).thenReturn(email);
        ChangeRequest changeRequest = mock(ChangeRequest.class);
        when(changeRequest.getRequestedBy()).thenReturn(user);
        when(changeRequest.getChangeType()).thenReturn(changeType);
        return changeRequest;
    }

    private Request mockRequestForCreated(String userName, String email, String serverName, Long requestId) {
        User user = mock(User.class);
        when(user.getName()).thenReturn(userName);
        when(user.getEmail()).thenReturn(email);
        ResourceGroup rg = mock(ResourceGroup.class);
        when(rg.getServerName()).thenReturn(serverName);
        ContainerImage image = mock(ContainerImage.class);
        when(image.getImageName()).thenReturn("ubuntu");
        when(image.getImageVersion()).thenReturn("22.04");
        Request request = mock(Request.class);
        when(request.getUser()).thenReturn(user);
        when(request.getResourceGroup()).thenReturn(rg);
        when(request.getContainerImage()).thenReturn(image);
        when(request.getUbuntuUsername()).thenReturn("testuser");
        when(request.getUbuntuPassword()).thenReturn("InitPass1!");
        when(request.getRequestId()).thenReturn(requestId);
        return request;
    }

    private PodExternalPort mockPodPort(String purpose, int externalPort) {
        PodExternalPort port = mock(PodExternalPort.class);
        when(port.getUsagePurpose()).thenReturn(purpose);
        when(port.getExternalPort()).thenReturn(externalPort);
        return port;
    }
}
