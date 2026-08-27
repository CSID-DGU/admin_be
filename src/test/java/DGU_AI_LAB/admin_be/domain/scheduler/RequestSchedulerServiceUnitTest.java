package DGU_AI_LAB.admin_be.domain.scheduler;

import DGU_AI_LAB.admin_be.domain.alarm.service.AlarmService;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.requests.service.RequestExpiryService;
import DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup;
import DGU_AI_LAB.admin_be.global.util.MessageUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * deleteExpiredRequest()가 (부분 실패 시) 예외를 던지면, processExpiredRequests()의
 * catch 블록이 실제로 관리자 알림을 트리거하는지 — 즉 RequestExpiryService 쪽 수정이
 * 이 알림 경로에 실제로 도달 가능한지를 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RequestSchedulerService - 만료 정리 실패 시 관리자 알림 트리거")
class RequestSchedulerServiceUnitTest {

    @Mock private RequestRepository requestRepository;
    @Mock private AlarmService alarmService;
    @Mock private RequestExpiryService requestExpiryService;
    @Mock private MessageUtils messageUtils;
    @Mock private RequestNotificationService requestNotificationService;

    @Mock private ResourceGroup mockRg;

    private RequestSchedulerService service;

    @BeforeEach
    void setUp() {
        service = new RequestSchedulerService(
                requestRepository, alarmService, requestExpiryService, messageUtils, requestNotificationService
        );
        when(mockRg.getServerName()).thenReturn("FARM-01");
    }

    private Request buildMockedRequest(Long requestId) {
        Request request = mock(Request.class);
        when(request.getRequestId()).thenReturn(requestId);
        when(request.getUbuntuUsername()).thenReturn("testuser");
        when(request.getResourceGroup()).thenReturn(mockRg);
        return request;
    }

    @Test
    @DisplayName("deleteExpiredRequest가 예외를 던지면 관리자 Slack 알림이 발송된다")
    void deleteExpiredRequestThrows_triggersAdminAlert() {
        LocalDateTime now = LocalDateTime.now();
        Request request = buildMockedRequest(1L);
        when(requestRepository.findAllWithUserByExpiredDateBefore(any(), eq(Status.FULFILLED)))
                .thenReturn(List.of(request));
        doThrow(new RuntimeException("Pod 삭제 실패"))
                .when(requestExpiryService).deleteExpiredRequest(1L);

        service.processExpiredRequests(now);

        verify(alarmService).sendAdminSlackNotification(eq("FARM-01"), any());
        verify(alarmService).sendSlackAlert(any(), any());
    }

    @Test
    @DisplayName("deleteExpiredRequest가 정상 종료되면 실패 알림은 발송되지 않는다")
    void deleteExpiredRequestSucceeds_noFailureAlert() {
        LocalDateTime now = LocalDateTime.now();
        Request request = buildMockedRequest(2L);
        when(requestRepository.findAllWithUserByExpiredDateBefore(any(), eq(Status.FULFILLED)))
                .thenReturn(List.of(request));

        service.processExpiredRequests(now);

        verify(requestExpiryService).deleteExpiredRequest(2L);
        verify(alarmService, never()).sendAdminSlackNotification(any(), any());
        verify(alarmService, never()).sendSlackAlert(any(), any());
    }

    @Test
    @DisplayName("만료 대상이 여러 건이면 실패한 건만 알림이 발송되고 나머지는 계속 처리된다")
    void partialFailureAmongMultipleRequests_onlyFailedOneAlertsAndProcessingContinues() {
        LocalDateTime now = LocalDateTime.now();
        Request failing = buildMockedRequest(3L);
        Request succeeding = buildMockedRequest(4L);
        when(requestRepository.findAllWithUserByExpiredDateBefore(any(), eq(Status.FULFILLED)))
                .thenReturn(List.of(failing, succeeding));
        doThrow(new RuntimeException("실패"))
                .when(requestExpiryService).deleteExpiredRequest(3L);

        service.processExpiredRequests(now);

        verify(requestExpiryService).deleteExpiredRequest(3L);
        verify(requestExpiryService).deleteExpiredRequest(4L); // 하나 실패해도 나머지는 계속 처리
        verify(alarmService, times(1)).sendAdminSlackNotification(any(), any());
    }

    @Test
    @DisplayName("만료 대상이 없으면 아무것도 조회/처리하지 않는다")
    void noExpiredRequests_doesNothing() {
        LocalDateTime now = LocalDateTime.now();
        when(requestRepository.findAllWithUserByExpiredDateBefore(any(), eq(Status.FULFILLED)))
                .thenReturn(List.of());

        service.processExpiredRequests(now);

        verify(requestExpiryService, never()).deleteExpiredRequest(any());
        verify(alarmService, never()).sendAdminSlackNotification(any(), any());
    }
}
