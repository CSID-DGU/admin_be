package DGU_AI_LAB.admin_be.domain.alarm.service;

import DGU_AI_LAB.admin_be.domain.alarm.dto.SlackMessageDto;
import DGU_AI_LAB.admin_be.domain.containerImage.entity.ContainerImage;
import DGU_AI_LAB.admin_be.domain.groups.entity.Group;
import DGU_AI_LAB.admin_be.domain.portRequests.entity.PortRequests;
import DGU_AI_LAB.admin_be.domain.requests.entity.RequestGroup;
import org.springframework.context.support.ResourceBundleMessageSource;
import DGU_AI_LAB.admin_be.domain.pod.entity.PodExternalPort;
import DGU_AI_LAB.admin_be.domain.pod.repository.PodExternalPortRepository;
import DGU_AI_LAB.admin_be.domain.requests.entity.ChangeRequest;
import DGU_AI_LAB.admin_be.domain.requests.entity.ChangeType;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.global.server.ServerProfileProperties;
import DGU_AI_LAB.admin_be.global.server.ServerProfileRegistry;
import DGU_AI_LAB.admin_be.global.util.MessageUtils;
import org.springframework.mail.SimpleMailMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private static final String FARM_REQUEST_WEBHOOK = "https://hooks.slack.com/farm-request";
    private static final String FARM_REQUEST_CHANNEL_ID = "C0FARMREQ";
    private static final String NOTI_CHANNEL_ID = "C0NOTI";

    @Spy
    private ServerProfileRegistry serverProfileRegistry = new ServerProfileRegistry(
            new ServerProfileProperties(Map.of(
                    "FARM", new ServerProfileProperties.Server("farm.example.org", "farm-admin", "farm-request",
                            new ServerProfileProperties.PortForwarding(30000, 9300, 98)),
                    "LAB", new ServerProfileProperties.Server("lab.example.org", "lab-admin", null, null))),
            new MockEnvironment()
                    .withProperty("slack-webhook-url.farm-admin", FARM_WEBHOOK)
                    .withProperty("slack-webhook-url.lab-admin", LAB_WEBHOOK)
                    .withProperty("slack-webhook-url.farm-request", FARM_REQUEST_WEBHOOK)
                    .withProperty("slack-channel-id.farm-request", FARM_REQUEST_CHANNEL_ID));

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(alarmService, "notiLogWebhookUrl", NOTI_WEBHOOK);
        ReflectionTestUtils.setField(alarmService, "errorLogWebhookUrl", ERROR_WEBHOOK);
        ReflectionTestUtils.setField(alarmService, "from", "noreply@dgu.ac.kr");
        when(redisTemplate.opsForList()).thenReturn(listOperations);
    }

    @Nested
    @DisplayName("성격별 전송 창구")
    class Gateways {

        private SlackMessageDto queued() {
            ArgumentCaptor<SlackMessageDto> captor = ArgumentCaptor.forClass(SlackMessageDto.class);
            verify(listOperations).rightPush(eq(QUEUE_KEY), captor.capture());
            return captor.getValue();
        }

        @Test
        @DisplayName("조치 필요 알림은 문구 양식에 값을 채워 오류 채널로 적재하고, 숫자는 콤마가 붙지 않게 문자열로 넘긴다")
        void alertNeedsAction_goesToErrorChannel() {
            when(messageUtils.get("notification.admin.revoke.job-failed", "1234", "pod-a", "POD_DELETE_FAILED"))
                    .thenReturn("회수 실패 1234");

            alarmService.alertNeedsAction("notification.admin.revoke.job-failed", 1234L, "pod-a", "POD_DELETE_FAILED");

            SlackMessageDto dto = queued();
            assertThat(dto.getType()).isEqualTo(SlackMessageDto.MessageType.WEBHOOK);
            assertThat(dto.getWebhookUrl()).isEqualTo(ERROR_WEBHOOK);
            assertThat(dto.getMessage()).isEqualTo("회수 실패 1234");
        }

        @Test
        @DisplayName("문구 양식을 읽지 못해도(DB 장애 등) 알림을 잃지 않고 키와 값을 그대로 보낸다")
        void alertNeedsAction_sendsKeyAndValues_whenTemplateUnreadable() {
            when(messageUtils.get(eq("notification.admin.approval.revert-failed"), any(), any()))
                    .thenThrow(new RuntimeException("DB 연결 실패"));

            alarmService.alertNeedsAction("notification.admin.approval.revert-failed", "FARM", 7L);

            SlackMessageDto dto = queued();
            assertThat(dto.getWebhookUrl()).isEqualTo(ERROR_WEBHOOK);
            assertThat(dto.getMessage()).isEqualTo("notification.admin.approval.revert-failed [FARM, 7]");
        }

        @Test
        @DisplayName("Redis 장애 시 큐를 거치지 않고 오류 채널로 직접 보낸다")
        void alertNeedsAction_sendsDirectly_whenRedisDown() {
            when(redisTemplate.opsForList()).thenThrow(new RuntimeException("Redis 연결 실패"));
            when(messageUtils.get("notification.admin.mail-failed", "h***@dgu.ac.kr")).thenReturn("메일 실패");
            when(messageUtils.get("notification.error.redis-fallback")).thenReturn(" (직접 전송)");

            alarmService.alertNeedsAction("notification.admin.mail-failed", "h***@dgu.ac.kr");

            verify(slackApiService).sendWebhook(ERROR_WEBHOOK, "메일 실패 (직접 전송)");
        }

        @Test
        @DisplayName("전송이 끝내 실패해도 호출한 쪽으로 예외를 올리지 않는다")
        void alertNeedsAction_neverThrows() {
            when(redisTemplate.opsForList()).thenThrow(new RuntimeException("Redis 연결 실패"));
            doThrow(new RuntimeException("Slack 연결 실패")).when(slackApiService).sendWebhook(any(), any());

            assertThatCode(() -> alarmService.alertNeedsAction("notification.admin.mail-failed", "h***@dgu.ac.kr"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("처리 기록은 알림 기록 채널로 적재한다")
        void recordLog_goesToNotiChannel() {
            when(messageUtils.get("notification.admin.delete.success", "FARM", "user1", "FARM")).thenReturn("정리 완료");

            alarmService.recordLog("notification.admin.delete.success", "FARM", "user1", "FARM");

            SlackMessageDto dto = queued();
            assertThat(dto.getWebhookUrl()).isEqualTo(NOTI_WEBHOOK);
            assertThat(dto.getMessage()).isEqualTo("정리 완료");
        }

        @Test
        @DisplayName("채널 알림에 넣는 글 값은 Slack mrkdwn 이스케이프를 거친다 — <!channel> 같은 글이 호출로 바뀌지 않는다")
        void channelAlerts_escapeTextArgs() {
            when(messageUtils.get("notification.monitor.log", "&lt;!channel&gt; 홍&amp;길동", "hong@dgu.ac.kr", "제목"))
                    .thenReturn("발송 기록");
            when(messageUtils.get("notification.admin.home-cleanup.fail", "&lt;@U1&gt;", "7", "&lt;!here&gt;"))
                    .thenReturn("홈 삭제 실패");

            alarmService.notifyUser("<!channel> 홍&길동", "hong@dgu.ac.kr", "제목", "내용");
            alarmService.alertNeedsAction("notification.admin.home-cleanup.fail", "<@U1>", 7L, "<!here>");

            List<SlackMessageDto> queued = allQueued(3);
            assertThat(queued.get(0).getMessage()).isEqualTo("내용");
            assertThat(queued.get(1).getMessage()).isEqualTo("발송 기록");
            assertThat(queued.get(2).getMessage()).isEqualTo("홈 삭제 실패");
        }

        private List<SlackMessageDto> allQueued(int count) {
            ArgumentCaptor<SlackMessageDto> captor = ArgumentCaptor.forClass(SlackMessageDto.class);
            verify(listOperations, times(count)).rightPush(eq(QUEUE_KEY), captor.capture());
            return captor.getAllValues();
        }

        @Test
        @DisplayName("사용자 안내는 메일과 Slack DM을 보내고 발송 기록을 알림 기록 채널에 남긴다")
        void notifyUser_sendsMailDmAndReceipt() {
            when(messageUtils.get("notification.monitor.log", "홍길동", "hong@dgu.ac.kr", "제목")).thenReturn("발송 기록");

            alarmService.notifyUser("홍길동", "hong@dgu.ac.kr", "제목", "내용");

            verify(mailSender).send(any(SimpleMailMessage.class));
            List<SlackMessageDto> queued = allQueued(2);
            SlackMessageDto dm = queued.get(0);
            assertThat(dm.getType()).isEqualTo(SlackMessageDto.MessageType.DM);
            assertThat(dm.getUsername()).isEqualTo("홍길동");
            assertThat(dm.getEmail()).isEqualTo("hong@dgu.ac.kr");
            assertThat(dm.getMessage()).isEqualTo("내용");
            assertThat(queued.get(1).getWebhookUrl()).isEqualTo(NOTI_WEBHOOK);
            assertThat(queued.get(1).getMessage()).isEqualTo("발송 기록");
        }

        @Test
        @DisplayName("메일이 실패하면 오류 채널로 알리고 발송 기록은 남기지 않는다 — DM은 그대로 보낸다")
        void notifyUser_alertsInsteadOfReceipt_whenMailFails() {
            doThrow(new RuntimeException("SMTP 오류")).when(mailSender).send(any(SimpleMailMessage.class));
            when(messageUtils.get("notification.admin.mail-failed", "h***@dgu.ac.kr")).thenReturn("메일 실패");

            alarmService.notifyUser("홍길동", "hong@dgu.ac.kr", "제목", "내용");

            List<SlackMessageDto> queued = allQueued(2);
            assertThat(queued.get(0).getType()).isEqualTo(SlackMessageDto.MessageType.DM);
            assertThat(queued.get(1).getWebhookUrl()).isEqualTo(ERROR_WEBHOOK);
            assertThat(queued.get(1).getMessage()).isEqualTo("메일 실패");
        }

        @Test
        @DisplayName("Redis 장애 시 DM도 큐를 거치지 않고 직접 보낸다")
        void notifyUser_sendsDmDirectly_whenRedisDown() {
            when(redisTemplate.opsForList()).thenThrow(new RuntimeException("Redis 연결 실패"));
            when(messageUtils.get("notification.error.redis-fallback")).thenReturn(" (직접 전송)");

            alarmService.notifyUser("홍길동", "hong@dgu.ac.kr", "제목", "내용");

            verify(slackApiService).sendDM("홍길동", "hong@dgu.ac.kr", "내용 (직접 전송)");
        }
    }

    @Nested
    @DisplayName("sendNewRequestNotification — 서버별 채널 라우팅")
    class SendNewRequestNotification {

        @Test
        @DisplayName("신청서 채널을 따로 둔 서버(FARM)의 신청은 그 채널로 적재된다")
        void sendNewRequestNotification_routesToRequestChannel_whenServerHasOne() {
            Request request = mockRequest("홍길동", "FARM");
            when(messageUtils.get(anyString(), any(), any())).thenReturn("새 신청");

            alarmService.sendNewRequestNotification(request, List.of(), 0);

            ArgumentCaptor<SlackMessageDto> captor = ArgumentCaptor.forClass(SlackMessageDto.class);
            verify(listOperations).rightPush(eq(QUEUE_KEY), captor.capture());
            assertThat(captor.getValue().getWebhookUrl()).isEqualTo(FARM_REQUEST_WEBHOOK);
        }

        @Test
        @DisplayName("이메일 도메인이 e2e로 시작하는 테스트용 계정의 신청은 신청서 채널이 아니라 알림 기록 채널로 적재된다")
        void sendNewRequestNotification_routesToNotiChannel_forTestAccount() {
            Request request = mockRequest("tester", "FARM");
            when(request.getUser().getEmail()).thenReturn("Tester@E2E.local");
            when(messageUtils.get(anyString(), any(), any())).thenReturn("새 신청");

            alarmService.sendNewRequestNotification(request, List.of(), 0);

            ArgumentCaptor<SlackMessageDto> captor = ArgumentCaptor.forClass(SlackMessageDto.class);
            verify(listOperations).rightPush(eq(QUEUE_KEY), captor.capture());
            assertThat(captor.getValue().getWebhookUrl()).isEqualTo(NOTI_WEBHOOK);
        }

        @Test
        @DisplayName("테스트용 계정 판정은 도메인만 본다 — 아이디가 e2e로 시작하는 학교 메일은 실제 사용자다")
        void isTestAccount_looksAtDomainOnly() {
            assertThat(AlarmService.isTestAccount("hc1006@e2e.local")).isTrue();
            assertThat(AlarmService.isTestAccount("e2e@dgu.ac.kr")).isFalse();
            assertThat(AlarmService.isTestAccount("2022112431@dgu.ac.kr")).isFalse();
            assertThat(AlarmService.isTestAccount("no-at-sign")).isFalse();
            assertThat(AlarmService.isTestAccount(null)).isFalse();
        }

        @Test
        @DisplayName("신청서 채널이 없는 서버(LAB)의 신청은 관리 채널로 적재된다")
        void sendNewRequestNotification_routesToLabWebhook_forLabServer() {
            Request request = mockRequest("이순신", "LAB");
            when(messageUtils.get(anyString(), any(), any())).thenReturn("새 신청");

            alarmService.sendNewRequestNotification(request, List.of(), 0);

            ArgumentCaptor<SlackMessageDto> captor = ArgumentCaptor.forClass(SlackMessageDto.class);
            verify(listOperations).rightPush(eq(QUEUE_KEY), captor.capture());
            assertThat(captor.getValue().getWebhookUrl()).isEqualTo(LAB_WEBHOOK);
        }

        @Test
        @DisplayName("알 수 없는 서버 신청은 errorLog 채널로 폴백된다")
        void sendNewRequestNotification_fallsBackToErrorLog_forUnknownServer() {
            Request request = mockRequest("김철수", "UNKNOWN_SERVER");
            when(messageUtils.get(anyString(), any(), any())).thenReturn("새 신청");

            alarmService.sendNewRequestNotification(request, List.of(), 0);

            ArgumentCaptor<SlackMessageDto> captor = ArgumentCaptor.forClass(SlackMessageDto.class);
            verify(listOperations).rightPush(eq(QUEUE_KEY), captor.capture());
            assertThat(captor.getValue().getWebhookUrl()).isEqualTo(ERROR_WEBHOOK);
        }
    }

    @Nested
    @DisplayName("sendNewRequestNotification — 승인 판단용 전체 정보")
    class SendNewRequestNotificationContent {

        private String render(Request request, List<PortRequests> ports, long activeContainers) {
            ResourceBundleMessageSource source = new ResourceBundleMessageSource();
            source.setBasename("messages");
            source.setDefaultEncoding("UTF-8");
            ReflectionTestUtils.setField(alarmService, "messageUtils", new MessageUtils(source));

            alarmService.sendNewRequestNotification(request, ports, activeContainers);

            ArgumentCaptor<SlackMessageDto> captor = ArgumentCaptor.forClass(SlackMessageDto.class);
            verify(listOperations).rightPush(eq(QUEUE_KEY), captor.capture());
            return captor.getValue().getMessage();
        }

        @Test
        @DisplayName("신청자·자원·그룹·포트·기간·사용 목적을 모두 담는다")
        void includesEverythingKnownAboutTheRequest() {
            Request request = mockRequest("홍길동", "FARM");
            when(request.getRequestId()).thenReturn(1234L);
            when(request.getCreatedAt()).thenReturn(java.time.LocalDateTime.of(2026, 12, 17, 10, 5));
            when(request.getUsagePurpose()).thenReturn("첫째 줄\n둘째 줄");
            when(request.isEnableVnc()).thenReturn(true);
            when(request.getResourceGroup().getDescription()).thenReturn("RTX 3090 24GB");
            ContainerImage image = mock(ContainerImage.class);
            when(image.getImageName()).thenReturn("dguailab/decs");
            when(image.getImageVersion()).thenReturn("260915");
            when(request.getContainerImage()).thenReturn(image);
            Group group = mock(Group.class);
            when(group.getGroupName()).thenReturn("vision-team");
            RequestGroup requestGroup = mock(RequestGroup.class);
            when(requestGroup.getGroup()).thenReturn(group);
            when(request.getRequestGroups()).thenReturn(Set.of(requestGroup));
            PortRequests port = mock(PortRequests.class);
            when(port.getInternalPort()).thenReturn(6006);
            when(port.getUsagePurpose()).thenReturn("TensorBoard");

            String message = render(request, List.of(port), 2);

            assertThat(message).contains(
                    "2026-12-17 10:05 접수", "관리 번호 #1234", "이름: 홍길동", "학번: 20260000", "학과: 컴퓨터공학과",
                    "이메일: 홍길동@dgu.ac.kr", "전화번호: 010-0000-0000", "서버 계정(ID): testuser",
                    "지금 사용 중인 컨테이너: 2개", "GPU: 3090ti (RTX 3090 24GB)", "dguailab/decs:260915",
                    "공유 그룹: vision-team", "추가 포트: 6006번 (TensorBoard)", "noVNC): 사용",
                    "2026-12-17 ~ 2026-12-31 (총 14일)", "*사용 목적*\n첫째 줄\n둘째 줄");
            assertThat(message).doesNotContain("{").doesNotContain(":bell:");
        }

        @Test
        @DisplayName("폼 응답에 적은 팀 프로젝트 정보를 담고, Slack 제어 문자는 이스케이프한다")
        void includesTeamInfoFromFormAnswers() {
            Request request = mockRequest("홍길동", "FARM");
            when(request.getFormAnswers()).thenReturn(
                    "{\"purpose\":\"연구\",\"teamInfo\":\"vision-team / 홍길동, 김철수 <!here>\"}");

            String message = render(request, List.of(), 0);

            assertThat(message)
                    .contains("팀 프로젝트 정보(그룹·팀원): vision-team / 홍길동, 김철수 &lt;!here&gt;")
                    .doesNotContain("<!here>");
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", "{}", "{\"purpose\":\"연구\"}", "{\"teamInfo\":\"  \"}", "{\"teamInfo\":null}", "not json"})
        @DisplayName("팀 프로젝트 정보를 적지 않았거나 폼 응답을 읽을 수 없으면 없음으로 적는다")
        void teamInfo_isNone_whenAbsentOrUnreadable(String formAnswers) {
            Request request = mockRequest("홍길동", "FARM");
            when(request.getFormAnswers()).thenReturn(formAnswers);

            String message = render(request, List.of(), 0);

            assertThat(message).contains("팀 프로젝트 정보(그룹·팀원): 없음");
        }

        @Test
        @DisplayName("사용자가 적은 글의 <, >, &는 Slack 호출·링크로 바뀌지 않게 이스케이프한다")
        void escapesSlackControlCharacters() {
            Request request = mockRequest("홍길동", "FARM");
            when(request.getUsagePurpose()).thenReturn("<!channel> 모두 확인 & 승인");

            String message = render(request, List.of(), 0);

            assertThat(message).contains("&lt;!channel&gt; 모두 확인 &amp; 승인").doesNotContain("<!channel>");
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
        @DisplayName("포트 포워딩이 설정된 서버는 공인 주소와 포워딩된 공인 포트를 안내한다")
        void sendContainerCreatedEmail_usesForwardedPublicPort() {
            Request request = mockRequestForCreated("이순신", "lee@dgu.ac.kr", "farm", 6L);
            when(podExternalPortRepository.findByRequestRequestId(6L)).thenReturn(List.of());
            when(messageUtils.get(anyString(), any(), any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn("본문");

            alarmService.sendContainerCreatedEmail(request, "30022", "30888");

            verify(messageUtils).get(eq("email.container.created.body"),
                    any(), any(), any(), eq("9322"), eq("30888"), eq("farm.example.org"), any(), any());
        }

        @Test
        @DisplayName("포트 포워딩이 없는 서버는 NodePort를 그대로, 모르는 서버는 빈 주소로 안내한다")
        void sendContainerCreatedEmail_keepsNodePortWithoutForwarding() {
            Request lab = mockRequestForCreated("홍길동", "hong@dgu.ac.kr", "LAB", 7L);
            Request unknown = mockRequestForCreated("김철수", "kim@dgu.ac.kr", "DGX", 8L);
            when(podExternalPortRepository.findByRequestRequestId(any())).thenReturn(List.of());
            when(messageUtils.get(anyString(), any(), any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn("본문");

            alarmService.sendContainerCreatedEmail(lab, "30022", "30888");
            alarmService.sendContainerCreatedEmail(unknown, "30022", "30888");

            verify(messageUtils).get(eq("email.container.created.body"),
                    any(), any(), any(), eq("30022"), eq("30888"), eq("lab.example.org"), any(), any());
            verify(messageUtils).get(eq("email.container.created.body"),
                    any(), any(), any(), eq("30022"), eq("30888"), eq(""), any(), any());
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
    @DisplayName("sendContainerPortsChangedEmail")
    class SendContainerPortsChangedEmail {

        @Test
        @DisplayName("FARM은 저장된 NodePort를 공인 포트로 바꿔 새 접속 정보를 보낸다")
        void farmMapsNodePortsToPublicPorts() {
            Request request = mockRequestForCreated("이순신", "lee@dgu.ac.kr", "FARM", 5L);
            PodExternalPort ssh = mockPodPort("ssh", 30010);
            PodExternalPort jupyter = mockPodPort("jupyter", 30011);
            PodExternalPort tensor = mockPodPort("tensorboard", 30012);
            when(podExternalPortRepository.findByRequestRequestId(5L)).thenReturn(List.of(ssh, jupyter, tensor));
            when(messageUtils.get(anyString(), any())).thenReturn("제목");
            when(messageUtils.get(anyString(), any(), any(), any(), any(), any(), any())).thenReturn("본문");

            alarmService.sendContainerPortsChangedEmail(request);

            verify(messageUtils).get(eq("email.container.ports-changed.body"),
                    eq("이순신"), eq("testuser"), eq("9310"), eq("9311"), eq("farm.example.org"), eq("tensorboard(30012)"));
            ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
            verify(mailSender).send(captor.capture());
            assertThat(captor.getValue().getTo()).containsExactly("lee@dgu.ac.kr");
        }

        @Test
        @DisplayName("LAB은 NodePort를 그대로 안내한다")
        void labKeepsNodePorts() {
            Request request = mockRequestForCreated("홍길동", "hong@dgu.ac.kr", "LAB", 6L);
            PodExternalPort ssh = mockPodPort("ssh", 30010);
            when(podExternalPortRepository.findByRequestRequestId(6L)).thenReturn(List.of(ssh));
            when(messageUtils.get(anyString(), any())).thenReturn("제목");
            when(messageUtils.get(anyString(), any(), any(), any(), any(), any(), any())).thenReturn("본문");

            alarmService.sendContainerPortsChangedEmail(request);

            verify(messageUtils).get(eq("email.container.ports-changed.body"),
                    any(), any(), eq("30010"), eq(""), eq("lab.example.org"), eq("없음"));
        }
    }

    @Nested
    @DisplayName("신청서 채널 — 봇 전송과 스레드 댓글")
    class RequestChannelThread {

        private SlackMessageDto queued() {
            ArgumentCaptor<SlackMessageDto> captor = ArgumentCaptor.forClass(SlackMessageDto.class);
            verify(listOperations).rightPush(eq(QUEUE_KEY), captor.capture());
            return captor.getValue();
        }

        @Test
        @DisplayName("채널 ID가 있는 서버의 신청서는 봇으로 올리고, 메시지 식별자를 적을 신청 번호와 대신 보낼 webhook을 함께 싣는다")
        void newRequest_goesByBot_whenChannelIdKnown() {
            Request request = mockRequest("홍길동", "FARM");
            when(request.getRequestId()).thenReturn(42L);
            when(messageUtils.get(anyString(), any(Object[].class))).thenReturn("새 신청");

            alarmService.sendNewRequestNotification(request, List.of(), 0);

            SlackMessageDto dto = queued();
            assertThat(dto.getType()).isEqualTo(SlackMessageDto.MessageType.CHANNEL);
            assertThat(dto.getChannelId()).isEqualTo(FARM_REQUEST_CHANNEL_ID);
            assertThat(dto.getRequestId()).isEqualTo(42L);
            assertThat(dto.getThreadTs()).isNull();
            assertThat(dto.getWebhookUrl()).isEqualTo(FARM_REQUEST_WEBHOOK);
        }

        @Test
        @DisplayName("채널 ID가 없는 서버(LAB)의 신청서는 예전처럼 webhook으로 간다")
        void newRequest_goesByWebhook_whenNoChannelId() {
            Request request = mockRequest("홍길동", "LAB");
            when(messageUtils.get(anyString(), any(Object[].class))).thenReturn("새 신청");

            alarmService.sendNewRequestNotification(request, List.of(), 0);

            SlackMessageDto dto = queued();
            assertThat(dto.getType()).isEqualTo(SlackMessageDto.MessageType.WEBHOOK);
            assertThat(dto.getWebhookUrl()).isEqualTo(LAB_WEBHOOK);
        }

        @Test
        @DisplayName("테스트용 계정의 신청서는 봇으로 올릴 때도 서버 신청서 채널이 아니라 알림 기록 채널로만 간다")
        void newRequest_ofTestAccount_neverReachesServerChannel() {
            ReflectionTestUtils.setField(alarmService, "notiChannelId", NOTI_CHANNEL_ID);
            Request request = mockRequest("tester", "FARM");
            when(request.getUser().getEmail()).thenReturn("tester@e2e.local");
            when(messageUtils.get(anyString(), any(Object[].class))).thenReturn("새 신청");

            alarmService.sendNewRequestNotification(request, List.of(), 0);

            SlackMessageDto dto = queued();
            assertThat(dto.getType()).isEqualTo(SlackMessageDto.MessageType.CHANNEL);
            assertThat(dto.getChannelId()).isEqualTo(NOTI_CHANNEL_ID);
            assertThat(dto.getWebhookUrl()).isEqualTo(NOTI_WEBHOOK);
        }

        @Test
        @DisplayName("신청서 메시지 식별자를 알면 취소 알림을 그 메시지의 스레드 댓글로 단다")
        void cancel_repliesInThread_whenMessageKnown() {
            Request request = mockRequest("홍길동", "FARM");
            when(request.getSlackMessageTs()).thenReturn("1728200000.000100");
            when(messageUtils.get(anyString(), any(Object[].class))).thenReturn("취소 알림");

            alarmService.prepareRequestCancelledNotification(request).run();

            SlackMessageDto dto = queued();
            assertThat(dto.getType()).isEqualTo(SlackMessageDto.MessageType.CHANNEL);
            assertThat(dto.getChannelId()).isEqualTo(FARM_REQUEST_CHANNEL_ID);
            assertThat(dto.getThreadTs()).isEqualTo("1728200000.000100");
            assertThat(dto.getRequestId()).isNull();
            assertThat(dto.getWebhookUrl()).isEqualTo(FARM_REQUEST_WEBHOOK);
        }

        @Test
        @DisplayName("신청서 메시지 식별자를 모르면 취소 알림은 일반 메시지(webhook)로 간다")
        void cancel_goesByWebhook_whenMessageUnknown() {
            Request request = mockRequest("홍길동", "FARM");
            when(messageUtils.get(anyString(), any(Object[].class))).thenReturn("취소 알림");

            alarmService.prepareRequestCancelledNotification(request).run();

            SlackMessageDto dto = queued();
            assertThat(dto.getType()).isEqualTo(SlackMessageDto.MessageType.WEBHOOK);
            assertThat(dto.getWebhookUrl()).isEqualTo(FARM_REQUEST_WEBHOOK);
        }

        @Test
        @DisplayName("큐(Redis)가 죽었으면 봇으로 올릴 신청서도 webhook으로 바로 보낸다")
        void newRequest_sendsWebhookDirectly_whenRedisDown() {
            Request request = mockRequest("홍길동", "FARM");
            when(messageUtils.get(anyString(), any(Object[].class))).thenReturn("새 신청");
            when(messageUtils.get("notification.error.redis-fallback")).thenReturn("(직접 전송)");
            when(listOperations.rightPush(any(), any())).thenThrow(new RuntimeException("redis down"));

            alarmService.sendNewRequestNotification(request, List.of(), 0);

            verify(slackApiService).sendWebhook(FARM_REQUEST_WEBHOOK, "새 신청(직접 전송)");
            verify(slackApiService, never()).postToChannel(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("prepareRequestCancelledNotification")
    class PrepareRequestCancelledNotification {

        @Test
        @DisplayName("취소 알림은 신청서 제목과 같은 서버·접수 시각을 달고 서버별 신청서 채널로 가며, 실행하기 전에는 보내지 않는다")
        void routesToRequestChannel_onlyWhenRun() {
            Request request = mockRequest("홍길동", "FARM");
            when(request.getCreatedAt()).thenReturn(java.time.LocalDateTime.of(2026, 10, 6, 13, 5));
            when(messageUtils.get("notification.admin.request-cancelled",
                    "FARM", "2026-10-06 13:05", "홍길동")).thenReturn("취소 알림");

            Runnable send = alarmService.prepareRequestCancelledNotification(request);
            verify(listOperations, never()).rightPush(any(), any());

            send.run();

            ArgumentCaptor<SlackMessageDto> captor = ArgumentCaptor.forClass(SlackMessageDto.class);
            verify(listOperations).rightPush(eq(QUEUE_KEY), captor.capture());
            assertThat(captor.getValue().getWebhookUrl()).isEqualTo(FARM_REQUEST_WEBHOOK);
            assertThat(captor.getValue().getMessage()).isEqualTo("취소 알림");
        }

        @Test
        @DisplayName("테스트용 계정의 취소 알림은 신청서와 같이 알림 기록 채널로 간다")
        void routesToNotiChannel_forTestAccount() {
            Request request = mockRequest("tester", "FARM");
            when(request.getUser().getEmail()).thenReturn("tester@e2e.local");
            when(messageUtils.get(anyString(), any(Object[].class))).thenReturn("취소 알림");

            alarmService.prepareRequestCancelledNotification(request).run();

            ArgumentCaptor<SlackMessageDto> captor = ArgumentCaptor.forClass(SlackMessageDto.class);
            verify(listOperations).rightPush(eq(QUEUE_KEY), captor.capture());
            assertThat(captor.getValue().getWebhookUrl()).isEqualTo(NOTI_WEBHOOK);
        }
    }

    @Nested
    @DisplayName("sendRequestReceivedEmail")
    class SendRequestReceivedEmail {

        private Request receivedRequest() {
            Request request = mockRequestForCreated("홍길동", "hong@dgu.ac.kr", "FARM", 812L);
            when(request.getResourceGroup().getResourceGroupName()).thenReturn("A6000");
            when(request.getExpiresAt()).thenReturn(java.time.LocalDateTime.of(2026, 12, 31, 23, 59));
            when(request.getCreatedAt()).thenReturn(java.time.LocalDateTime.of(2026, 10, 6, 13, 5));
            return request;
        }

        @Test
        @DisplayName("신청자에게 관리 번호·서버·GPU·개발 환경·종료일·접수 시각을 담은 접수 확인 메일을 보낸다")
        void sendsReceiptToApplicant() {
            Request request = receivedRequest();
            when(messageUtils.get("email.request.received.subject", "FARM")).thenReturn("접수 제목");
            when(messageUtils.get("email.request.received.body",
                    "홍길동", "812", "FARM", "A6000", "ubuntu:22.04", "2026-12-31", "2026-10-06 13:05"))
                    .thenReturn("접수 본문");

            alarmService.sendRequestReceivedEmail(request);

            ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
            verify(mailSender).send(captor.capture());
            assertThat(captor.getValue().getTo()).containsExactly("hong@dgu.ac.kr");
            assertThat(captor.getValue().getSubject()).isEqualTo("접수 제목");
            assertThat(captor.getValue().getText()).isEqualTo("접수 본문");
        }

        @Test
        @DisplayName("접수 확인은 메일만 보낸다 — 같은 신청이 신청서 채널로 이미 가므로 Slack에는 따로 남기지 않는다")
        void doesNotPushToSlack() {
            alarmService.sendRequestReceivedEmail(receivedRequest());

            verify(listOperations, never()).rightPush(any(), any());
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
    @DisplayName("sendGroupAddedEmail")
    class SendGroupAddedEmail {

        @Test
        @DisplayName("그룹마다 홈 아래 폴더를 공유하는 명령을 본문에 담아 사용자에게 보낸다")
        void sendGroupAddedEmail_listsShareCommandPerGroup() {
            ChangeRequest changeRequest = mockChangeRequest("이순신", "lee@dgu.ac.kr", ChangeType.GROUP);
            when(messageUtils.get(anyString(), any())).thenReturn("제목");
            when(messageUtils.get("email.modification.approved.group.dir", "teama")).thenReturn("- teama 경로");
            when(messageUtils.get("email.modification.approved.group.dir", "teamb")).thenReturn("- teamb 경로");
            when(messageUtils.get(eq("email.modification.approved.group.body"), any(), any(), any(), any()))
                    .thenReturn("본문");

            alarmService.sendGroupAddedEmail(changeRequest, "승인", List.of("teama", "teamb"));

            verify(messageUtils).get("email.modification.approved.group.body", "이순신", "공유 그룹 추가", "승인",
                    "- teama 경로\n- teamb 경로");
            ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
            verify(mailSender).send(captor.capture());
            assertThat(captor.getValue().getTo()).containsExactly("lee@dgu.ac.kr");
            assertThat(captor.getValue().getText()).isEqualTo("본문");
        }

        @Test
        @DisplayName("실제 문구: 공유 명령과 안내를 담고, 없어진 팀 디렉터리(/home/_g_, ~/shared)는 안내하지 않는다")
        void sendGroupAddedEmail_rendersHomeFolderSharing() {
            ResourceBundleMessageSource source = new ResourceBundleMessageSource();
            source.setBasename("messages");
            source.setDefaultEncoding("UTF-8");
            ReflectionTestUtils.setField(alarmService, "messageUtils", new MessageUtils(source));
            ChangeRequest changeRequest = mockChangeRequest("이순신", "lee@dgu.ac.kr", ChangeType.GROUP);

            alarmService.sendGroupAddedEmail(changeRequest, "승인", List.of("teama", "teamb"));

            ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
            verify(mailSender).send(captor.capture());
            assertThat(captor.getValue().getText())
                    .contains("- teama 그룹: group-dir-share ~/<폴더 이름> teama",
                            "- teamb 그룹: group-dir-share ~/<폴더 이름> teamb",
                            "/home/<내 아이디>/<폴더 이름>", "폴더 하나는 그룹 하나와만")
                    .doesNotContain("_g_").doesNotContain("~/shared").doesNotContain("{");
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
        @DisplayName("변경 유형이 영어 코드가 아닌 한글 이름으로 이메일 제목에 들어간다")
        void sendModificationRejectedEmail_includesChangeTypeInSubject() {
            ChangeRequest changeRequest = mockChangeRequest("김철수", "kim@dgu.ac.kr", ChangeType.RESOURCE_GROUP);
            when(messageUtils.get(eq("email.modification.rejected.subject"), eq("GPU 변경")))
                    .thenReturn("[DGU AI LAB] GPU 변경 요청이 승인되지 않았어요");
            when(messageUtils.get(anyString(), any(), any(), any())).thenReturn("본문");

            alarmService.sendModificationRejectedEmail(changeRequest, "리소스 그룹 변경 불가");

            ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
            verify(mailSender).send(captor.capture());
            assertThat(captor.getValue().getSubject()).contains("GPU 변경");
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

    @Nested
    @DisplayName("sendContainerExtendedEmail")
    class SendContainerExtendedEmail {

        @Test
        @DisplayName("oldExpiresAt이 null이면 NPE 없이 '이전 기록 없음'이 본문에 전달된다")
        void sendContainerExtendedEmail_nullOldExpiresAt_rendersPlaceholder() {
            Request request = mockRequestForExtended("홍길동", "hong@dgu.ac.kr", "LAB", "pod-abc");
            when(podExternalPortRepository.findByRequestRequestId(any())).thenReturn(List.of());
            when(messageUtils.get(anyString(), any())).thenReturn("제목");
            when(messageUtils.get(anyString(), any(), any(), any(), any(), any(), any(), any())).thenReturn("본문");

            alarmService.sendContainerExtendedEmail(request, null,
                    java.time.LocalDateTime.of(2027, 12, 31, 23, 59, 59));

            verify(messageUtils).get(eq("email.container.extended.body"),
                    any(), any(), any(), any(), eq("이전 기록 없음"), any(), any());
        }

        @Test
        @DisplayName("oldExpiresAt이 있으면 날짜 문자열이 본문에 전달된다")
        void sendContainerExtendedEmail_withOldExpiresAt_passesDate() {
            Request request = mockRequestForExtended("홍길동", "hong@dgu.ac.kr", "LAB", "pod-abc");
            when(podExternalPortRepository.findByRequestRequestId(any())).thenReturn(List.of());
            when(messageUtils.get(anyString(), any())).thenReturn("제목");
            when(messageUtils.get(anyString(), any(), any(), any(), any(), any(), any(), any())).thenReturn("본문");

            alarmService.sendContainerExtendedEmail(request,
                    java.time.LocalDateTime.of(2026, 6, 30, 0, 0),
                    java.time.LocalDateTime.of(2027, 12, 31, 23, 59, 59));

            verify(messageUtils).get(eq("email.container.extended.body"),
                    any(), any(), any(), any(), eq("2026-06-30"), any(), any());
        }

        @Test
        @DisplayName("podName이 null이면 '미배정'이 본문에 전달된다")
        void sendContainerExtendedEmail_nullPodName_rendersPlaceholder() {
            Request request = mockRequestForExtended("홍길동", "hong@dgu.ac.kr", "LAB", null);
            when(podExternalPortRepository.findByRequestRequestId(any())).thenReturn(List.of());
            when(messageUtils.get(anyString(), any())).thenReturn("제목");
            when(messageUtils.get(anyString(), any(), any(), any(), any(), any(), any(), any())).thenReturn("본문");

            alarmService.sendContainerExtendedEmail(request,
                    java.time.LocalDateTime.of(2026, 6, 30, 0, 0),
                    java.time.LocalDateTime.of(2027, 12, 31, 23, 59, 59));

            verify(messageUtils).get(eq("email.container.extended.body"),
                    any(), any(), any(), any(), any(), eq("미배정"), any());
        }
    }

    private Request mockRequestForDeleted(String userName, String email, String serverName, String podName) {
        return mockRequestWithOptionalId(userName, email, serverName, podName, null);
    }

    private Request mockRequestForExtended(String userName, String email, String serverName, String podName) {
        return mockRequestWithOptionalId(userName, email, serverName, podName, 99L);
    }

    private Request mockRequestWithOptionalId(String userName, String email, String serverName, String podName, Long requestId) {
        User user = mock(User.class);
        when(user.getName()).thenReturn(userName);
        when(user.getEmail()).thenReturn(email);
        ResourceGroup rg = mock(ResourceGroup.class);
        when(rg.getServerName()).thenReturn(serverName);
        Request request = mock(Request.class);
        when(request.getUser()).thenReturn(user);
        when(request.getResourceGroup()).thenReturn(rg);
        when(request.getPodName()).thenReturn(podName);
        when(request.getUbuntuUsername()).thenReturn("testuser");
        if (requestId != null) {
            when(request.getRequestId()).thenReturn(requestId);
        }
        return request;
    }

    private Request mockRequest(String userName, String serverName) {
        User user = mock(User.class);
        when(user.getName()).thenReturn(userName);
        // sendNewRequestNotification이 승인 판단용 정보를 전부 채워 넣으므로, 실제 엔티티라면
        // nullable=false인 이 필드들도 여기서 채워야 한다(안 채우면 null 역참조로 시험이 깨진다).
        when(user.getStudentId()).thenReturn("20260000");
        when(user.getDepartment()).thenReturn("컴퓨터공학과");
        when(user.getEmail()).thenReturn(userName.toLowerCase() + "@dgu.ac.kr");
        when(user.getPhone()).thenReturn("010-0000-0000");
        ResourceGroup rg = mock(ResourceGroup.class);
        when(rg.getServerName()).thenReturn(serverName);
        when(rg.getResourceGroupName()).thenReturn("3090ti");
        Request request = mock(Request.class);
        when(request.getUser()).thenReturn(user);
        when(request.getResourceGroup()).thenReturn(rg);
        when(request.getUbuntuUsername()).thenReturn("testuser");
        when(request.getUsagePurpose()).thenReturn("테스트 목적");
        when(request.getExpiresAt()).thenReturn(java.time.LocalDateTime.of(2026, 12, 31, 23, 59));
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
