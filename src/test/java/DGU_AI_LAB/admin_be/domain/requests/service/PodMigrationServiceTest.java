package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.domain.warnings.service.SuspensionGuard;
import DGU_AI_LAB.admin_be.support.Alerts;
import DGU_AI_LAB.admin_be.global.alert.InMemoryAlertDeduplicator;
import DGU_AI_LAB.admin_be.domain.requests.job.JobClient;
import DGU_AI_LAB.admin_be.domain.requests.job.JobResults;
import DGU_AI_LAB.admin_be.domain.alarm.service.AlarmService;
import DGU_AI_LAB.admin_be.domain.nodes.entity.Node;
import DGU_AI_LAB.admin_be.domain.nodes.repository.NodeRepository;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.MigratePodRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.MigrateRegisterRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.RestartPodRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.CreatePodResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.JobResultResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.JobStepsResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.MigrationResultResponseDTO;
import DGU_AI_LAB.admin_be.domain.pod.entity.PodExternalPort;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.pod.repository.PodExternalPortRepository;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
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
    @Mock private NodeRepository nodeRepository;
    @Mock private JobClient jobClient;
    @Mock private PlatformTransactionManager transactionManager;
    @Mock private TransactionStatus transactionStatus;
    @Mock private AlarmService alarmService;
    @Mock private ContainerRestartThrottle restartThrottle;
    @Mock private SuspensionGuard suspensionGuard;
    @Mock private Request request;
    @Mock private User user;
    @Mock private ResourceGroup resourceGroup;

    private PodMigrationService service;

    @BeforeEach
    void setUp() {
        when(request.getJobId()).thenReturn(10L); // result()의 작업 번호와 같다
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        service = new PodMigrationService(requestRepository, suspensionGuard, podExternalPortRepository, nodeRepository, jobClient, transactionManager, alarmService,
                restartThrottle, new InMemoryAlertDeduplicator());
        when(nodeRepository.findAllByResourceGroup(resourceGroup)).thenReturn(List.of(node("FARM2"), node("FARM7")));
        when(request.getUbuntuUsername()).thenReturn("testuser");
        when(request.getPodName()).thenReturn("ailab-testuser-old");
        when(request.getRequestId()).thenReturn(1L);
        when(requestRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(request));
        when(request.getUser()).thenReturn(user);
        when(request.getResourceGroup()).thenReturn(resourceGroup);
    }

    private Node node(String nodeId) {
        return Node.builder().nodeId(nodeId).resourceGroup(resourceGroup).memorySizeGB(64).cpuCoreCount(16).build();
    }

    private PodExternalPort port(String purpose, int internalPort, int externalPort) {
        return PodExternalPort.builder().request(request).usagePurpose(purpose)
                .internalPort(internalPort).externalPort(externalPort).build();
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
        when(request.getJobId()).thenReturn(11L);
        when(jobClient.getResult(JobResults.KIND_MIGRATE, 1L))
                .thenReturn(result(JobResults.PHASE_SUCCESS, migrated(null)));

        service.settleFinishedMigration(1L);

        verify(request, never()).assignPodInfo(any(), any());
        verify(request, never()).endMigration();
    }

    @Test
    @DisplayName("이미 끝난 마이그레이션은 상태 검증 전에 앞당겨 반영한다")
    void settleAppliesFinishedResult() {
        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(request.getStatus()).thenReturn(Status.MIGRATING);
        when(jobClient.getResult(JobResults.KIND_MIGRATE, 1L))
                .thenReturn(result(JobResults.PHASE_SUCCESS, migrated(null)));

        service.settleFinishedMigration(1L);

        verify(request).assignPodInfo("ailab-testuser-new", "farm7");
        verify(request).endMigration();
    }

    @Test
    @DisplayName("아직 실행 중인 마이그레이션은 건드리지 않는다")
    void settleIgnoresRunningJob() {
        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(request.getStatus()).thenReturn(Status.MIGRATING);
        when(jobClient.getResult(JobResults.KIND_MIGRATE, 1L))
                .thenReturn(result(JobResults.PHASE_START, null));

        service.settleFinishedMigration(1L);

        verify(request, never()).endMigration();
    }

    @Test
    @DisplayName("마이그레이션 중이 아니면 작업 결과를 조회하지 않는다")
    void settleSkipsWhenNotMigrating() {
        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(request.getStatus()).thenReturn(Status.FULFILLED);

        service.settleFinishedMigration(1L);

        verify(jobClient, never()).getResult(anyString(), any());
    }

    @Test
    @DisplayName("시작하면 MIGRATING으로 바꾸고 기존 Pod·노드 목록·force로 작업을 등록한다")
    void startRegistersJob() {
        service.startMigration(1L, new MigratePodRequestDTO(List.of("farm2", "farm7"), null, true));

        verify(request).beginMigration();
        ArgumentCaptor<MigrateRegisterRequestDTO> captor = ArgumentCaptor.forClass(MigrateRegisterRequestDTO.class);
        verify(jobClient).registerMigrate(captor.capture());
        assertThat(captor.getValue()).isEqualTo(MigrateRegisterRequestDTO.move(1L, "ailab-testuser-old", "testuser",
                List.of("farm2", "farm7"), null, true));
    }

    @Test
    @DisplayName("관리자 재시작은 노드 목록 없이 현재 노드 재생성 작업을 등록하고 횟수를 세지 않는다")
    void adminRestartRegistersRecreateJob() {
        service.startRestart(1L, new RestartPodRequestDTO(false));

        verify(request).beginMigration();
        verify(jobClient).registerMigrate(MigrateRegisterRequestDTO.restart(1L, "ailab-testuser-old", "testuser", false));
        verifyNoInteractions(restartThrottle, nodeRepository);
    }

    @Test
    @DisplayName("본인 재시작은 본문이 없으면 변경분을 유지하는 재생성 작업을 등록한다")
    void ownRestartKeepsChangesByDefault() {
        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(user.getUserId()).thenReturn(7L);

        service.startOwnRestart(7L, 1L, null);

        verify(restartThrottle).acquire(7L);
        verify(jobClient).registerMigrate(MigrateRegisterRequestDTO.restart(1L, "ailab-testuser-old", "testuser", true));
    }

    @Test
    @DisplayName("남의 신청은 상태를 바꾸기 전에 거절하고 횟수도 세지 않는다")
    void ownRestartRejectsOtherUsersRequest() {
        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(user.getUserId()).thenReturn(7L);

        assertThatThrownBy(() -> service.startOwnRestart(8L, 1L, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN_REQUEST);
        verify(request, never()).beginMigration();
        verify(requestRepository, never()).findByIdForUpdate(any());
        verifyNoInteractions(restartThrottle, jobClient);
    }

    @Test
    @DisplayName("재시작할 수 없는 상태면 횟수를 쓰지 않는다")
    void ownRestartInWrongStatusDoesNotConsumeQuota() {
        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(user.getUserId()).thenReturn(7L);
        doThrow(new BusinessException(ErrorCode.INVALID_REQUEST_STATUS)).when(request).beginMigration();

        assertThatThrownBy(() -> service.startOwnRestart(7L, 1L, null)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(restartThrottle, jobClient);
    }

    @Test
    @DisplayName("횟수를 넘기면 작업을 등록하지 않는다")
    void ownRestartOverQuotaRegistersNothing() {
        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(user.getUserId()).thenReturn(7L);
        doThrow(new BusinessException(ErrorCode.TOO_MANY_CONTAINER_RESTARTS)).when(restartThrottle).acquire(7L);

        assertThatThrownBy(() -> service.startOwnRestart(7L, 1L, null)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(jobClient);
    }

    @Test
    @DisplayName("남의 신청의 재시작 결과는 조회할 수 없다")
    void ownLatestRejectsOtherUsersRequest() {
        when(user.getUserId()).thenReturn(7L);
        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.getOwnLatestMigration(8L, 1L)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(jobClient);
    }

    @Test
    @DisplayName("신청의 리소스 그룹 밖 노드가 후보에 있으면 상태를 바꾸지 않고 거절한다")
    void startRejectsNodeOutsideResourceGroup() {
        assertThatThrownBy(() -> service.startMigration(1L, new MigratePodRequestDTO(List.of("farm2", "farm1"), null, true)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("farm1")
                .extracting("errorCode").isEqualTo(ErrorCode.MIGRATION_NODE_OUTSIDE_RESOURCE_GROUP);
        verify(request, never()).beginMigration();
        verifyNoInteractions(jobClient);
    }

    @Test
    @DisplayName("등록한 작업 번호를 신청에 남긴다")
    void startRecordsJobId() {
        when(jobClient.registerMigrate(any())).thenReturn(3700L);
        when(request.getStatus()).thenReturn(Status.MIGRATING);

        service.startMigration(1L, new MigratePodRequestDTO(List.of("farm2"), null, null));

        verify(request).recordJob(3700L);
    }

    @Test
    @DisplayName("FULFILLED가 아니면 작업을 등록하지 않는다")
    void startRejectsWrongStatus() {
        doThrow(new BusinessException(ErrorCode.INVALID_REQUEST_STATUS)).when(request).beginMigration();

        assertThatThrownBy(() -> service.startMigration(1L, new MigratePodRequestDTO(List.of("farm2"), null, null)))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(jobClient);
    }

    @Test
    @DisplayName("등록이 실패하면 FULFILLED로 되돌리고 오류를 전파한다")
    void startRevertsWhenRegistrationFails() {
        when(request.getStatus()).thenReturn(Status.MIGRATING);
        doThrow(new BusinessException(ErrorCode.INFRA_REQUEST_REJECTED)).when(jobClient).registerMigrate(any());

        assertThatThrownBy(() -> service.startMigration(1L, new MigratePodRequestDTO(List.of("farm2"), null, null)))
                .isInstanceOf(BusinessException.class);
        verify(request).endMigration();
    }

    @Test
    @DisplayName("등록 응답이 실패로 보여도 작업이 도는 중이면(응답만 늦음) MIGRATING으로 두고 그 작업 번호를 이어받는다")
    void startAdoptsRunningJobWhenRegistrationResponseFails() {
        when(request.getStatus()).thenReturn(Status.MIGRATING);
        doThrow(new BusinessException("작업 등록 중 오류: timeout", ErrorCode.POD_MIGRATION_FAILED,
                new java.util.concurrent.TimeoutException()))
                .when(jobClient).registerMigrate(any());
        when(jobClient.getResult("migrate", 1L)).thenReturn(
                new JobResultResponseDTO("1", "migrate", 3800L, "RETRY", null, null, null));

        service.startMigration(1L, new MigratePodRequestDTO(List.of("farm2"), null, null));

        verify(request, never()).endMigration();
        verify(request).recordJob(3800L);
    }

    @Test
    @DisplayName("요청이 config-server에 닿지도 않았으면 작업을 조회하지 않고 바로 FULFILLED로 되돌린다")
    void startRevertsImmediatelyWhenServerUnreachable() {
        when(request.getStatus()).thenReturn(Status.MIGRATING);
        doThrow(new BusinessException("작업 등록 중 오류", ErrorCode.POD_MIGRATION_FAILED,
                new java.net.ConnectException("refused")))
                .when(jobClient).registerMigrate(any());

        assertThatThrownBy(() -> service.startMigration(1L, new MigratePodRequestDTO(List.of("farm2"), null, null)))
                .isInstanceOf(BusinessException.class);
        verify(jobClient, never()).getResult(anyString(), any());
        verify(request).endMigration();
    }

    @Test
    @DisplayName("등록 실패 뒤 작업 상태도 조회하지 못하면 되돌리지 않고(작업이 돌 수 있다) 관리자에게 알린다")
    void startKeepsMigratingWhenLookupAlsoFails() {
        when(request.getStatus()).thenReturn(Status.MIGRATING);
        doThrow(new BusinessException("작업 등록 중 오류: timeout", ErrorCode.POD_MIGRATION_FAILED,
                new java.util.concurrent.TimeoutException()))
                .when(jobClient).registerMigrate(any());
        when(jobClient.getResult("migrate", 1L)).thenThrow(new BusinessException(ErrorCode.EXTERNAL_API_ERROR));

        assertThatThrownBy(() -> service.startMigration(1L, new MigratePodRequestDTO(List.of("farm2"), null, null)))
                .isInstanceOf(BusinessException.class);
        verify(request, never()).endMigration();
        assertThat(Alerts.needsAction(alarmService)).filteredOn(alert -> alert.contains("migration.register-unconfirmed")).hasSize(1);
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
        assertThat(Alerts.needsAction(alarmService)).isEmpty();
    }

    @Test
    @DisplayName("옮기면서 포트가 바뀌면 커밋 뒤 사용자에게 새 접속 정보를 메일로 알린다")
    void completeMigratedWithNewPortsMailsUser() {
        when(request.getStatus()).thenReturn(Status.MIGRATING);
        when(podExternalPortRepository.findByRequestRequestId(1L)).thenReturn(List.of(port("ssh", 22, 32001)));

        service.completeMigrationJob(1L, migrated(null));

        var order = inOrder(request, alarmService);
        order.verify(request).endMigration();
        order.verify(alarmService).sendContainerPortsChangedEmail(request);
    }

    @Test
    @DisplayName("옮겼어도 포트가 그대로면 메일을 보내지 않는다")
    void completeMigratedWithSamePortsSendsNoMail() {
        when(request.getStatus()).thenReturn(Status.MIGRATING);
        when(podExternalPortRepository.findByRequestRequestId(1L)).thenReturn(List.of(port("ssh", 22, 32010)));

        service.completeMigrationJob(1L, migrated(null));

        verify(request).endMigration();
        verify(alarmService, never()).sendContainerPortsChangedEmail(any());
    }

    @Test
    @DisplayName("결과에 포트가 없으면 안내할 접속 정보가 없으므로 메일을 보내지 않는다")
    void completeMigratedWithoutPortsSendsNoMail() {
        when(request.getStatus()).thenReturn(Status.MIGRATING);
        when(podExternalPortRepository.findByRequestRequestId(1L)).thenReturn(List.of(port("ssh", 22, 32001)));
        JobResultResponseDTO.Result noPorts = new JobResultResponseDTO.Result(null, null, "ailab-testuser-new", "farm7",
                null, "migrated", null, "farm2", "farm7", "ailab-testuser-old", null);

        service.completeMigrationJob(1L, noPorts);

        verify(request).endMigration();
        verify(alarmService, never()).sendContainerPortsChangedEmail(any());
    }

    @Test
    @DisplayName("안내 메일 발송이 실패해도 반영된 결과는 그대로 두고 예외를 올리지 않는다")
    void completeMigratedMailFailureDoesNotPropagate() {
        when(request.getStatus()).thenReturn(Status.MIGRATING);
        doThrow(new RuntimeException("smtp down")).when(alarmService).sendContainerPortsChangedEmail(request);

        service.completeMigrationJob(1L, migrated(null));

        verify(request).endMigration();
        verify(alarmService).sendContainerPortsChangedEmail(request);
    }

    @Test
    @DisplayName("상태가 이미 바뀌어 반영하지 않았으면 메일도 보내지 않는다")
    void completeIgnoredSendsNoMail() {
        when(request.getStatus()).thenReturn(Status.FULFILLED);

        service.completeMigrationJob(1L, migrated(null));

        verify(alarmService, never()).sendContainerPortsChangedEmail(any());
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
        assertThat(Alerts.needsAction(alarmService)).hasSize(1);
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
        assertThat(Alerts.needsAction(alarmService)).filteredOn(alert -> alert.contains("migration.old-pod-cleanup-failed")).hasSize(1);
    }

    @Test
    @DisplayName("작업이 실패하면 FULFILLED로 되돌리고 알린다")
    void failReverts() {
        when(request.getStatus()).thenReturn(Status.MIGRATING);

        service.failMigrationJob(1L, new JobResultResponseDTO("1", "migrate", 9L, "FAIL", "POD_NOT_FOUND", null, null));

        verify(request).endMigration();
        assertThat(Alerts.needsAction(alarmService)).filteredOn(alert -> alert.contains("POD_NOT_FOUND")).hasSize(1);
    }

    @Test
    @DisplayName("결과 불명이면 MIGRATING으로 두고 알리기만 한다")
    void unresolvedKeepsMigrating() {
        service.reportUnresolvedMigrationJob(1L, new JobResultResponseDTO("1", "migrate", 9L, "UNKNOWN", "DEGRADED", null, null));

        verify(request, never()).endMigration();
        assertThat(Alerts.needsAction(alarmService)).filteredOn(alert -> alert.contains("migration.job-unresolved")).hasSize(1);
    }

    @Test
    @DisplayName("진행 중인 작업이면 그 작업에서 끝난 단계 이름을 함께 준다")
    void latestMigrationCarriesCompletedStepsWhileRunning() {
        when(requestRepository.existsById(1L)).thenReturn(true);
        when(jobClient.getResult("migrate", 1L)).thenReturn(
                new JobResultResponseDTO("1", "migrate", 9L, "START", null, null, null));
        when(jobClient.getSteps("migrate", 1L)).thenReturn(new JobStepsResponseDTO("1", "migrate", List.of(
                new JobStepsResponseDTO.Job(9L, null, null, "START", null, List.of(
                        new JobStepsResponseDTO.Step(null, "SELECT_NODE", "SUCCESS", 1, null, null, null, null, null),
                        new JobStepsResponseDTO.Step(null, "COMMIT_IMAGE", "SUCCESS", 1, null, null, null, null, null),
                        new JobStepsResponseDTO.Step(null, "CREATE_POD_K8S", "FAIL", 1, null, null, null, null, null))),
                new JobStepsResponseDTO.Job(8L, null, null, "SUCCESS", null, List.of(
                        new JobStepsResponseDTO.Step(null, "DELETE_POD_K8S", "SUCCESS", 1, null, null, null, null, null))))));

        assertThat(service.getLatestMigration(1L).completedSteps()).containsExactly("SELECT_NODE", "COMMIT_IMAGE");
    }

    @Test
    @DisplayName("단계 기록을 읽지 못해도 결과 조회는 실패하지 않는다")
    void latestMigrationSurvivesStepLookupFailure() {
        when(requestRepository.existsById(1L)).thenReturn(true);
        when(jobClient.getResult("migrate", 1L)).thenReturn(
                new JobResultResponseDTO("1", "migrate", 9L, "START", null, null, null));
        when(jobClient.getSteps("migrate", 1L)).thenThrow(new BusinessException(ErrorCode.EXTERNAL_API_ERROR));

        assertThat(service.getLatestMigration(1L).completedSteps()).isEmpty();
    }

    @Test
    @DisplayName("마지막 마이그레이션 결과를 화면용으로 바꿔 준다")
    void latestMigration() {
        when(requestRepository.existsById(1L)).thenReturn(true);
        when(jobClient.getResult("migrate", 1L)).thenReturn(
                new JobResultResponseDTO("1", "migrate", 9L, "SUCCESS", null, "2026-09-15 12:00:00", migrated(null)));

        MigrationResultResponseDTO result = service.getLatestMigration(1L);

        assertThat(result.phase()).isEqualTo("SUCCESS");
        assertThat(result.status()).isEqualTo("migrated");
        assertThat(result.fromNode()).isEqualTo("farm2");
        assertThat(result.toNode()).isEqualTo("farm7");
    }

    @Test
    @DisplayName("이용 정지 중인 사용자는 본인 컨테이너를 다시 만들 수 없다 — 다시 만들면 접속 포트가 새로 열린다")
    void ownRestartIsRefusedWhileSuspended() {
        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(user.getUserId()).thenReturn(7L);
        doThrow(new BusinessException(ErrorCode.USER_SUSPENDED)).when(suspensionGuard).requireNotSuspended(7L);

        assertThatThrownBy(() -> service.startOwnRestart(7L, 1L, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.USER_SUSPENDED);
        verify(request, never()).beginMigration();
        verifyNoInteractions(restartThrottle, jobClient);
    }

    @Test
    @DisplayName("관리자가 이용 정지 중인 사용자의 컨테이너를 다시 만들면 접속 포트를 막힌 채로 만들게 한다")
    void adminRestartOfSuspendedUserKeepsAccessBlocked() {
        when(user.getUserId()).thenReturn(7L);
        when(suspensionGuard.isSuspended(7L)).thenReturn(true);

        service.startRestart(1L, new RestartPodRequestDTO(false));

        verify(jobClient).registerMigrate(
                MigrateRegisterRequestDTO.restart(1L, "ailab-testuser-old", "testuser", false).accessBlocked(true));
    }

    @Test
    @DisplayName("옮긴 뒤에는 새 접속 포트에 이용 정지가 걸려 있는지 다시 맞춘다")
    void completedMigrationReblocksNewPorts() {
        when(request.getStatus()).thenReturn(Status.MIGRATING);
        when(user.getUserId()).thenReturn(7L);

        service.completeMigrationJob(1L, migrated(null));

        verify(suspensionGuard).reblockAfterPortsCreated(7L);
    }
}
