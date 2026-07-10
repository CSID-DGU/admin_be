package DGU_AI_LAB.admin_be.domain.scheduler;

import DGU_AI_LAB.admin_be.domain.alarm.service.AlarmService;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.global.util.MessageUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RequestNotificationService")
class RequestNotificationServiceTest {

    @InjectMocks
    private RequestNotificationService notificationService;

    @Mock
    private RequestRepository requestRepository;

    @Mock
    private AlarmService alarmService;

    @Mock
    private MessageUtils messageUtils;

    private Request buildRequest(Long userId, String userName, String userEmail,
                                  String serverName, String ubuntuUsername, LocalDateTime expiresAt) {
        User user = mock(User.class);
        when(user.getName()).thenReturn(userName);
        when(user.getEmail()).thenReturn(userEmail);

        ResourceGroup rg = mock(ResourceGroup.class);
        when(rg.getServerName()).thenReturn(serverName);

        Request request = mock(Request.class);
        when(request.getUser()).thenReturn(user);
        when(request.getResourceGroup()).thenReturn(rg);
        when(request.getUbuntuUsername()).thenReturn(ubuntuUsername);
        when(request.getExpiresAt()).thenReturn(expiresAt);

        return request;
    }

    @Nested
    @DisplayName("sendPreExpiryNotification")
    class SendPreExpiryNotification {

        @Test
        @DisplayName("대상 날짜 범위를 startOfDay ~ LocalTime.MAX로 조회한다")
        void queriesCorrectDateRange() {
            LocalDateTime targetDate = LocalDateTime.of(2025, 11, 17, 10, 0, 0);
            LocalDateTime expectedStart = LocalDate.of(2025, 11, 17).atStartOfDay();
            LocalDateTime expectedEnd = LocalDate.of(2025, 11, 17).atTime(LocalTime.MAX);

            when(requestRepository.findAllByExpiresAtBetweenAndStatus(any(), any(), any()))
                    .thenReturn(List.of());

            notificationService.sendPreExpiryNotification(targetDate, "7일");

            verify(requestRepository).findAllByExpiresAtBetweenAndStatus(
                    eq(expectedStart), eq(expectedEnd), eq(Status.FULFILLED)
            );
        }

        @Test
        @DisplayName("끝 날짜가 LocalTime.MAX (23:59:59.999999999)로 설정된다")
        void endTimeIsLocalTimeMax() {
            LocalDateTime targetDate = LocalDateTime.of(2025, 11, 17, 10, 0, 0);

            ArgumentCaptor<LocalDateTime> endCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
            when(requestRepository.findAllByExpiresAtBetweenAndStatus(any(), endCaptor.capture(), any()))
                    .thenReturn(List.of());

            notificationService.sendPreExpiryNotification(targetDate, "7일");

            assertThat(endCaptor.getValue().toLocalTime()).isEqualTo(LocalTime.MAX);
        }

        @Test
        @DisplayName("조회된 요청이 없으면 알림을 발송하지 않는다")
        void doesNotSendAlert_whenNoRequests() {
            when(requestRepository.findAllByExpiresAtBetweenAndStatus(any(), any(), any()))
                    .thenReturn(List.of());

            notificationService.sendPreExpiryNotification(LocalDateTime.now(), "7일");

            verify(alarmService, never()).sendAllAlerts(any(), any(), any(), any());
        }

        @Test
        @DisplayName("조회된 요청마다 alarmService.sendAllAlerts를 호출한다")
        void sendsAlertForEachRequest() {
            LocalDateTime expiresAt = LocalDateTime.of(2025, 11, 17, 12, 0, 0);
            Request req1 = buildRequest(1L, "유저A", "a@dgu.ac.kr", "FARM-01", "usera", expiresAt);
            Request req2 = buildRequest(2L, "유저B", "b@dgu.ac.kr", "LAB-01", "userb", expiresAt);

            when(requestRepository.findAllByExpiresAtBetweenAndStatus(any(), any(), any()))
                    .thenReturn(List.of(req1, req2));
            when(messageUtils.get(anyString(), any(Object[].class))).thenReturn("mock");

            notificationService.sendPreExpiryNotification(LocalDateTime.now(), "7일");

            verify(alarmService, times(2)).sendAllAlerts(any(), any(), any(), any());
        }

        @Test
        @DisplayName("dayLabel이 알림 메시지 구성에 사용된다")
        void dayLabelIsPassedToMessageUtils() {
            LocalDateTime expiresAt = LocalDateTime.of(2025, 11, 14, 9, 0, 0);
            Request request = buildRequest(1L, "유저", "u@dgu.ac.kr", "FARM-01", "userx", expiresAt);

            when(requestRepository.findAllByExpiresAtBetweenAndStatus(any(), any(), any()))
                    .thenReturn(List.of(request));
            when(messageUtils.get(anyString(), any(Object[].class))).thenReturn("mock");

            notificationService.sendPreExpiryNotification(LocalDateTime.now(), "3일");

            verify(messageUtils).get(eq("notification.pre-expiry.subject"), eq("3일"));
            verify(messageUtils).get(eq("notification.pre-expiry.body"),
                    eq("유저"), eq("3일"), anyString(), eq("FARM-01"), eq("userx"));
        }

        @Test
        @DisplayName("단일 요청에 대해 사용자 이름과 이메일로 알림을 발송한다")
        void sendsAlertWithCorrectUserInfo() {
            LocalDateTime expiresAt = LocalDateTime.of(2025, 11, 14, 9, 0, 0);
            Request request = buildRequest(1L, "홍길동", "hong@dgu.ac.kr", "FARM-01", "hong", expiresAt);

            when(requestRepository.findAllByExpiresAtBetweenAndStatus(any(), any(), any()))
                    .thenReturn(List.of(request));
            when(messageUtils.get(anyString(), any(Object[].class))).thenReturn("mock");

            notificationService.sendPreExpiryNotification(LocalDateTime.now(), "1일");

            verify(alarmService).sendAllAlerts(eq("홍길동"), eq("hong@dgu.ac.kr"), any(), any());
        }

        @Test
        @DisplayName("특정 요청에서 예외가 발생해도 나머지 요청의 알림은 계속 발송한다")
        void continuesProcessing_whenOneRequestThrows() {
            LocalDateTime expiresAt = LocalDateTime.of(2025, 11, 14, 9, 0, 0);

            Request badRequest = mock(Request.class);
            when(badRequest.getUser()).thenThrow(new RuntimeException("DB error"));

            Request goodRequest = buildRequest(2L, "유저B", "b@dgu.ac.kr", "LAB-01", "userb", expiresAt);

            when(requestRepository.findAllByExpiresAtBetweenAndStatus(any(), any(), any()))
                    .thenReturn(List.of(badRequest, goodRequest));
            when(messageUtils.get(anyString(), any(Object[].class))).thenReturn("mock");

            notificationService.sendPreExpiryNotification(LocalDateTime.now(), "7일");

            verify(alarmService, times(1)).sendAllAlerts(any(), any(), any(), any());
        }

        @Test
        @DisplayName("모든 요청에서 예외가 발생하면 알림 발송 횟수는 0이다")
        void sendsNoAlerts_whenAllRequestsThrow() {
            Request badRequest1 = mock(Request.class);
            when(badRequest1.getUser()).thenThrow(new RuntimeException("error1"));
            Request badRequest2 = mock(Request.class);
            when(badRequest2.getUser()).thenThrow(new RuntimeException("error2"));

            when(requestRepository.findAllByExpiresAtBetweenAndStatus(any(), any(), any()))
                    .thenReturn(List.of(badRequest1, badRequest2));

            notificationService.sendPreExpiryNotification(LocalDateTime.now(), "1일");

            verify(alarmService, never()).sendAllAlerts(any(), any(), any(), any());
        }

        @Test
        @DisplayName("alarmService 호출 실패 시에도 나머지 요청 처리를 계속한다")
        void continuesProcessing_whenAlarmServiceThrows() {
            LocalDateTime expiresAt = LocalDateTime.of(2025, 11, 14, 9, 0, 0);
            Request req1 = buildRequest(1L, "유저A", "a@dgu.ac.kr", "FARM-01", "usera", expiresAt);
            Request req2 = buildRequest(2L, "유저B", "b@dgu.ac.kr", "FARM-01", "userb", expiresAt);

            when(requestRepository.findAllByExpiresAtBetweenAndStatus(any(), any(), any()))
                    .thenReturn(List.of(req1, req2));
            when(messageUtils.get(anyString(), any(Object[].class))).thenReturn("mock");
            doThrow(new RuntimeException("Slack error"))
                    .doNothing()
                    .when(alarmService).sendAllAlerts(any(), any(), any(), any());

            notificationService.sendPreExpiryNotification(LocalDateTime.now(), "3일");

            verify(alarmService, times(2)).sendAllAlerts(any(), any(), any(), any());
        }

        @Test
        @DisplayName("targetDate의 날짜만 사용하고 시각은 무시한다")
        void usesOnlyDatePartOfTargetDate() {
            LocalDateTime targetDateAtNoon = LocalDateTime.of(2025, 11, 20, 12, 30, 0);
            LocalDateTime targetDateAtMidnight = LocalDateTime.of(2025, 11, 20, 0, 0, 0);

            when(requestRepository.findAllByExpiresAtBetweenAndStatus(any(), any(), any()))
                    .thenReturn(List.of());

            notificationService.sendPreExpiryNotification(targetDateAtNoon, "7일");
            notificationService.sendPreExpiryNotification(targetDateAtMidnight, "7일");

            ArgumentCaptor<LocalDateTime> startCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
            verify(requestRepository, times(2))
                    .findAllByExpiresAtBetweenAndStatus(startCaptor.capture(), any(), any());

            List<LocalDateTime> starts = startCaptor.getAllValues();
            assertThat(starts.get(0)).isEqualTo(starts.get(1));
            assertThat(starts.get(0).toLocalTime()).isEqualTo(LocalTime.MIDNIGHT);
        }
    }
}
