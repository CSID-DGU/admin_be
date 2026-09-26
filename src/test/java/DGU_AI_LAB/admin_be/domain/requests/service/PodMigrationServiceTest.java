package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.domain.alarm.service.AlarmService;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.MigratePodRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.MigrateRegisterRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.CreatePodResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.JobResultResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.MigrationResultResponseDTO;
import DGU_AI_LAB.admin_be.domain.pod.entity.PodExternalPort;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.pod.repository.PodExternalPortRepository;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PodMigrationService")
class PodMigrationServiceTest {

    @Mock private RequestRepository requestRepository;
    @Mock private PodExternalPortRepository podExternalPortRepository;
    @Mock private OperationJobService operationJobService;
    @Mock private PlatformTransactionManager transactionManager;
    @Mock private TransactionStatus transactionStatus;
    @Mock private AlarmService alarmService;
    @Mock private Request request;

    private PodMigrationService service;

    @BeforeEach
    void setUp() {
        when(request.getMigrationJobId()).thenReturn(10L); // result()의 작업 번호와 같다
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        service = new PodMigrationService(requestRepository, podExternalPortRepository, operationJobService, transactionManager, alarmService);
        when(request.getUbuntuUsername()).thenReturn("testuser");
        when(request.getPodName()).thenReturn("ailab-testuser-old");
        when(requestRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(request));
    }

    private static JobResultResponseDTO.Result migrated(String cleanup) {
        return new JobResultResponseDTO.Result(null, null, "ailab-testuser-new", "farm7",
                List.of(new CreatePodResponseDTO.PortInfo("ssh", 22, 32010)),
                "migrated", null, "farm2", "farm7", "ailab-testuser-old", cleanup);
    }

    private static JobResultResponseDTO result(String phase, JobResultResponseDTO.Result made) {
        return new JobResultResponseDTO("1", "migrate", 10L, phase, null, null, made);
    }

    @Test
    @DisplayName("재마이그레이션 직후 보이는 이전 작업의 성공은 앞당겨 반영하지 않는다")
    void settleIgnoresPreviousJobResult() {
        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(request.getStatus()).thenReturn(Status.MIGRATING);
        when(request.getMigrationJobId()).thenReturn(11L);
        when(operationJobService.getResult(OperationJobService.KIND_MIGRATE, 1L))
                .thenReturn(result(OperationJobService.PHASE_SUCCESS, migrated(null)));

        service.settleFinishedMigration(1L);

        verify(request, never()).assignPodInfo(any(), any());
        verify(request, never()).endMigration();
    }

    @Test
    @DisplayName("이미 끝난 마이그레이션은 상태 검증 전에 앞당겨 반영한다")
    void settleAppliesFinishedResult() {
        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(request.getStatus()).thenReturn(Status.MIGRATING);
        when(operationJobService.getResult(OperationJobService.KIND_MIGRATE, 1L))
                .thenReturn(result(OperationJobService.PHASE_SUCCESS, migrated(null)));

        service.settleFinishedMigration(1L);

        verify(request).assignPodInfo("ailab-testuser-new", "farm7");
        verify(request).endMigration();
    }

    @Test
    @DisplayName("아직 실행 중인 마이그레이션은 건드리지 않는다")
    void settleIgnoresRunningJob() {
        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(request.getStatus()).thenReturn(Status.MIGRATING);
        when(operationJobService.getResult(OperationJobService.KIND_MIGRATE, 1L))
                .thenReturn(result(OperationJobService.PHASE_START, null));

        service.settleFinishedMigration(1L);

        verify(request, never()).endMigration();
    }

    @Test
    @DisplayName("마이그레이션 중이 아니면 작업 결과를 조회하지 않는다")
    void settleSkipsWhenNotMigrating() {
        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(request.getStatus()).thenReturn(Status.FULFILLED);

        service.settleFinishedMigration(1L);

        verify(operationJobService, never()).getResult(anyString(), any());
    }

    @Test
    @DisplayName("시작하면 MIGRATING으로 바꾸고 기존 Pod·노드 목록·force로 작업을 등록한다")
    void startRegistersJob() {
        service.startMigration(1L, new MigratePodRequestDTO(List.of("farm2", "farm7"), null, true));

        verify(request).beginMigration();
        ArgumentCaptor<MigrateRegisterRequestDTO> captor = ArgumentCaptor.forClass(MigrateRegisterRequestDTO.class);
        verify(operationJobService).registerMigrate(captor.capture());
        assertThat(captor.getValue()).isEqualTo(new MigrateRegisterRequestDTO(1L, "ailab-testuser-old", "testuser",
                List.of("farm2", "farm7"), null, true));
    }

    @Test
    @DisplayName("등록한 작업 번호를 신청에 남긴다")
    void startRecordsJobId() {
        when(operationJobService.registerMigrate(any())).thenReturn(3700L);
        when(request.getStatus()).thenReturn(Status.MIGRATING);

        service.startMigration(1L, new MigratePodRequestDTO(List.of("farm2"), null, null));

        verify(request).recordMigrationJob(3700L);
    }

    @Test
    @DisplayName("FULFILLED가 아니면 작업을 등록하지 않는다")
    void startRejectsWrongStatus() {
        doThrow(new BusinessException(ErrorCode.INVALID_REQUEST_STATUS)).when(request).beginMigration();

        assertThatThrownBy(() -> service.startMigration(1L, new MigratePodRequestDTO(List.of("farm2"), null, null)))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(operationJobService);
    }

    @Test
    @DisplayName("등록이 실패하면 FULFILLED로 되돌리고 오류를 전파한다")
    void startRevertsWhenRegistrationFails() {
        when(request.getStatus()).thenReturn(Status.MIGRATING);
        doThrow(new BusinessException(ErrorCode.INFRA_REQUEST_REJECTED)).when(operationJobService).registerMigrate(any());

        assertThatThrownBy(() -> service.startMigration(1L, new MigratePodRequestDTO(List.of("farm2"), null, null)))
                .isInstanceOf(BusinessException.class);
        verify(request).endMigration();
    }

    @Test
    @DisplayName("옮겼으면 새 Pod·노드·포트로 바꾸고 FULFILLED로 돌린다")
    void completeMigratedUpdatesPod() {
        when(request.getStatus()).thenReturn(Status.MIGRATING);

        service.completeMigrationJob(1L, migrated(null));

        verify(request).assignPodInfo("ailab-testuser-new", "farm7");
        verify(podExternalPortRepository).deleteByRequestRequestId(1L);
        verify(podExternalPortRepository).save(any(PodExternalPort.class));
        verify(request).endMigration();
        verify(alarmService, never()).sendSlackAlert(anyString(), any());
    }

    @Test
    @DisplayName("건너뛰었으면 Pod 정보는 그대로 두고 FULFILLED로만 돌린다")
    void completeSkippedKeepsPod() {
        when(request.getStatus()).thenReturn(Status.MIGRATING);
        JobResultResponseDTO.Result skipped = new JobResultResponseDTO.Result(null, null, null, null, List.of(),
                "skipped", "no_candidate_node", "farm2", null, "ailab-testuser-old", null);

        service.completeMigrationJob(1L, skipped);

        verify(request, never()).assignPodInfo(any(), any());
        verifyNoInteractions(podExternalPortRepository);
        verify(request).endMigration();
    }

    @Test
    @DisplayName("성공 결과가 없으면 건너뜀으로 확정하지 않고 MIGRATING에 둔 채 한 번만 알린다")
    void completeWithoutResultKeepsMigratingAndAlertsOnce() {
        when(request.getStatus()).thenReturn(Status.MIGRATING);

        service.completeMigrationJob(1L, null);
        service.completeMigrationJob(1L, null);

        verify(request, never()).endMigration();
        verify(request, never()).assignPodInfo(any(), any());
        verify(alarmService, times(1)).sendSlackAlert(anyString(), any());
    }

    @Test
    @DisplayName("상태가 이미 바뀌었으면 결과를 반영하지 않는다")
    void completeIgnoresChangedStatus() {
        when(request.getStatus()).thenReturn(Status.FULFILLED);

        service.completeMigrationJob(1L, migrated(null));

        verify(request, never()).assignPodInfo(any(), any());
        verify(request, never()).endMigration();
    }

    @Test
    @DisplayName("기존 Pod 정리가 실패했으면 반영은 하고 관리자에게 알린다")
    void completeAlertsOldPodCleanupFailure() {
        when(request.getStatus()).thenReturn(Status.MIGRATING);

        service.completeMigrationJob(1L, migrated("failed"));

        verify(request).endMigration();
        verify(alarmService).sendSlackAlert(contains("기존 Pod 정리 실패"), any());
    }

    @Test
    @DisplayName("작업이 실패하면 FULFILLED로 되돌리고 알린다")
    void failReverts() {
        when(request.getStatus()).thenReturn(Status.MIGRATING);

        service.failMigrationJob(1L, new JobResultResponseDTO("1", "migrate", 9L, "FAIL", "POD_NOT_FOUND", null, null));

        verify(request).endMigration();
        verify(alarmService).sendSlackAlert(contains("POD_NOT_FOUND"), any());
    }

    @Test
    @DisplayName("결과 불명이면 MIGRATING으로 두고 알리기만 한다")
    void unresolvedKeepsMigrating() {
        service.reportUnresolvedMigrationJob(1L, new JobResultResponseDTO("1", "migrate", 9L, "UNKNOWN", "DEGRADED", null, null));

        verify(request, never()).endMigration();
        verify(alarmService).sendSlackAlert(contains("확인 필요"), any());
    }

    @Test
    @DisplayName("마지막 마이그레이션 결과를 화면용으로 바꿔 준다")
    void latestMigration() {
        when(requestRepository.existsById(1L)).thenReturn(true);
        when(operationJobService.getResult("migrate", 1L)).thenReturn(
                new JobResultResponseDTO("1", "migrate", 9L, "SUCCESS", null, "2026-09-15 12:00:00", migrated(null)));

        MigrationResultResponseDTO result = service.getLatestMigration(1L);

        assertThat(result.phase()).isEqualTo("SUCCESS");
        assertThat(result.status()).isEqualTo("migrated");
        assertThat(result.fromNode()).isEqualTo("farm2");
        assertThat(result.toNode()).isEqualTo("farm7");
    }
}
