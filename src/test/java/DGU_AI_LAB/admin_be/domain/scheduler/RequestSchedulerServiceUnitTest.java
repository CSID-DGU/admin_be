package DGU_AI_LAB.admin_be.domain.scheduler;

import DGU_AI_LAB.admin_be.domain.alarm.service.AlarmService;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.JobResultResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.requests.service.AdminRequestCommandService;
import DGU_AI_LAB.admin_be.domain.requests.service.OperationJobService;
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
    @Mock private AdminRequestCommandService adminRequestCommandService;
    @Mock private OperationJobService operationJobService;

    @Mock private ResourceGroup mockRg;

    private RequestSchedulerService service;

    @BeforeEach
    void setUp() {
        service = new RequestSchedulerService(
                requestRepository, alarmService, requestExpiryService, messageUtils, requestNotificationService,
                adminRequestCommandService, operationJobService
        );
        when(mockRg.getServerName()).thenReturn("FARM-01");
        // 기본: 등록된 생성 작업이 없는 신청 (승인 도중 admin_be가 죽어 작업 등록 전에 멈춘 경우)
        when(operationJobService.getResult(eq(OperationJobService.KIND_PROVISION), any())).thenReturn(provisionJob(OperationJobService.PHASE_NONE));
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

    @Test
    @DisplayName("정지된 PROCESSING 요청은 revertToPendingIfStillProcessing으로 복구를 시도한다")
    void reconcile_staleProcessing_delegatesToRevert() {
        Request request = buildMockedRequest(10L);
        when(requestRepository.findAllByStatusAndUpdatedAtBefore(eq(Status.PROCESSING), any()))
                .thenReturn(List.of(request));
        when(requestRepository.findAllByStatusAndUpdatedAtBefore(eq(Status.MIGRATING), any()))
                .thenReturn(List.of());

        service.reconcileStaleInFlightRequests();

        verify(adminRequestCommandService).revertToPendingIfStillProcessing(10L, "FARM-01");
        verify(alarmService).sendSlackAlert(any(), any());
    }

    @Test
    @DisplayName("정지된 MIGRATING 요청은 자동 복구를 시도하지 않고 알림만 보낸다")
    void reconcile_staleMigrating_alertsWithoutAutoRevert() {
        Request request = buildMockedRequest(20L);
        when(requestRepository.findAllByStatusAndUpdatedAtBefore(eq(Status.PROCESSING), any()))
                .thenReturn(List.of());
        when(requestRepository.findAllByStatusAndUpdatedAtBefore(eq(Status.MIGRATING), any()))
                .thenReturn(List.of(request));

        service.reconcileStaleInFlightRequests();

        verify(adminRequestCommandService, never()).revertToPendingIfStillProcessing(any(), any());
        verify(alarmService).sendSlackAlert(any(), any());
    }

    @Test
    @DisplayName("정지된 요청이 없으면 알림도 복구 시도도 없다")
    void reconcile_noStaleRequests_doesNothing() {
        when(requestRepository.findAllByStatusAndUpdatedAtBefore(any(), any()))
                .thenReturn(List.of());

        service.reconcileStaleInFlightRequests();

        verify(adminRequestCommandService, never()).revertToPendingIfStillProcessing(any(), any());
        verify(alarmService, never()).sendSlackAlert(any(), any());
    }

    private static JobResultResponseDTO provisionJob(String phase) {
        return new JobResultResponseDTO(null, OperationJobService.KIND_PROVISION, null, phase, null, null, null);
    }

    @Test
    @DisplayName("정지된 PROCESSING 요청이라도 생성 작업이 아직 실행 중이면 되돌리지 않는다 — 되돌리면 재승인 때 컨테이너가 두 번 만들어진다")
    void reconcile_staleProcessing_jobStillRunning_doesNotRevert() {
        Request request = buildMockedRequest(11L);
        when(requestRepository.findAllByStatusAndUpdatedAtBefore(eq(Status.PROCESSING), any())).thenReturn(List.of(request));
        when(operationJobService.getResult(OperationJobService.KIND_PROVISION, 11L))
                .thenReturn(provisionJob(OperationJobService.PHASE_START));

        service.reconcileStaleInFlightRequests();

        verify(adminRequestCommandService, never()).revertToPendingIfStillProcessing(any(), any());
        verify(alarmService, never()).sendSlackAlert(any(), any());
    }

    @Test
    @DisplayName("생성 작업이 실패로 끝난 정지 PROCESSING 요청은 되돌린다")
    void reconcile_staleProcessing_jobFailed_reverts() {
        Request request = buildMockedRequest(12L);
        when(requestRepository.findAllByStatusAndUpdatedAtBefore(eq(Status.PROCESSING), any())).thenReturn(List.of(request));
        when(operationJobService.getResult(OperationJobService.KIND_PROVISION, 12L))
                .thenReturn(provisionJob(OperationJobService.PHASE_FAIL));

        service.reconcileStaleInFlightRequests();

        verify(adminRequestCommandService).revertToPendingIfStillProcessing(12L, "FARM-01");
    }

    @Test
    @DisplayName("자원을 남긴 실패(DEGRADED)는 정지된 PROCESSING이어도 되돌리지 않는다")
    void reconcile_staleProcessing_jobDegraded_doesNotRevert() {
        Request request = buildMockedRequest(16L);
        when(requestRepository.findAllByStatusAndUpdatedAtBefore(eq(Status.PROCESSING), any())).thenReturn(List.of(request));
        when(operationJobService.getResult(OperationJobService.KIND_PROVISION, 16L)).thenReturn(
                new JobResultResponseDTO(null, OperationJobService.KIND_PROVISION, null, OperationJobService.PHASE_FAIL, "DEGRADED", null, null));

        service.reconcileStaleInFlightRequests();

        verify(adminRequestCommandService, never()).revertToPendingIfStillProcessing(any(), any());
    }

    @Test
    @DisplayName("생성 작업이 성공·결과 불명이면 작업 결과 폴러에 맡기고 되돌리지 않는다")
    void reconcile_staleProcessing_jobFinishedOrUnknown_leftToPoller() {
        Request succeeded = buildMockedRequest(13L);
        Request unknown = buildMockedRequest(14L);
        when(requestRepository.findAllByStatusAndUpdatedAtBefore(eq(Status.PROCESSING), any())).thenReturn(List.of(succeeded, unknown));
        when(operationJobService.getResult(OperationJobService.KIND_PROVISION, 13L))
                .thenReturn(provisionJob(OperationJobService.PHASE_SUCCESS));
        when(operationJobService.getResult(OperationJobService.KIND_PROVISION, 14L))
                .thenReturn(provisionJob(OperationJobService.PHASE_UNKNOWN));

        service.reconcileStaleInFlightRequests();

        verify(adminRequestCommandService, never()).revertToPendingIfStillProcessing(any(), any());
    }

    @Test
    @DisplayName("생성 작업 상태를 조회하지 못하면 되돌리지 않는다")
    void reconcile_staleProcessing_jobLookupFails_doesNotRevert() {
        Request request = buildMockedRequest(15L);
        when(requestRepository.findAllByStatusAndUpdatedAtBefore(eq(Status.PROCESSING), any())).thenReturn(List.of(request));
        when(operationJobService.getResult(OperationJobService.KIND_PROVISION, 15L))
                .thenThrow(new RuntimeException("config-server down"));

        service.reconcileStaleInFlightRequests();

        verify(adminRequestCommandService, never()).revertToPendingIfStillProcessing(any(), any());
    }

    private static JobResultResponseDTO revokeJob(String phase) {
        return new JobResultResponseDTO(null, OperationJobService.KIND_REVOKE, 900L, phase, null, null, null);
    }

    @Test
    @DisplayName("회수 작업 없이 방치된 EXPIRING은 FULFILLED로 되돌린다 — 선점 뒤 등록 전에 admin_be가 멈춘 경우")
    void reconcile_staleExpiring_withoutJob_reverts() {
        Request request = buildMockedRequest(30L);
        when(requestRepository.findAllByStatusAndUpdatedAtBefore(eq(Status.EXPIRING), any())).thenReturn(List.of(request));
        when(operationJobService.getResult(OperationJobService.KIND_REVOKE, 30L)).thenReturn(revokeJob(OperationJobService.PHASE_NONE));

        service.reconcileStaleInFlightRequests();

        verify(requestExpiryService).revertStaleExpiring(30L);
    }

    @Test
    @DisplayName("회수 작업이 있으면(실행 중·끝남·불명) EXPIRING을 되돌리지 않고 결과 폴러에 맡긴다")
    void reconcile_staleExpiring_withJob_leftToPoller() {
        Request request = buildMockedRequest(31L);
        when(requestRepository.findAllByStatusAndUpdatedAtBefore(eq(Status.EXPIRING), any())).thenReturn(List.of(request));
        for (String phase : List.of(OperationJobService.PHASE_START, OperationJobService.PHASE_SUCCESS,
                OperationJobService.PHASE_FAIL, OperationJobService.PHASE_UNKNOWN)) {
            when(operationJobService.getResult(OperationJobService.KIND_REVOKE, 31L)).thenReturn(revokeJob(phase));

            service.reconcileStaleInFlightRequests();
        }

        verify(requestExpiryService, never()).revertStaleExpiring(any());
    }

    @Test
    @DisplayName("회수 작업 상태를 조회하지 못하면 EXPIRING을 되돌리지 않는다")
    void reconcile_staleExpiring_lookupFails_doesNotRevert() {
        Request request = buildMockedRequest(32L);
        when(requestRepository.findAllByStatusAndUpdatedAtBefore(eq(Status.EXPIRING), any())).thenReturn(List.of(request));
        when(operationJobService.getResult(OperationJobService.KIND_REVOKE, 32L)).thenThrow(new RuntimeException("down"));

        service.reconcileStaleInFlightRequests();

        verify(requestExpiryService, never()).revertStaleExpiring(any());
    }
}
