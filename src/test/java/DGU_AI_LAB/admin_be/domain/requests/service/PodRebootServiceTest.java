package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.domain.alarm.service.AlarmService;
import DGU_AI_LAB.admin_be.domain.containerImage.entity.ContainerImage;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.CreatePodResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.MigratePodResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.SaveRequestResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PodRebootService")
class PodRebootServiceTest {

    private static final Long REQUEST_ID = 1L;
    private static final Long OWNER_ID = 10L;

    @Mock private RequestRepository requestRepository;
    @Mock private PodService podService;
    @Mock private PodMigrationService podMigrationService;
    @Mock private PlatformTransactionManager transactionManager;
    @Mock private TransactionStatus transactionStatus;
    @Mock private AlarmService alarmService;
    @Mock private ThreadPoolTaskExecutor rebootExecutor;

    @Mock private Request mockRequest;
    @Mock private User mockUser;
    @Mock private ContainerImage mockImage;
    @Mock private ResourceGroup mockResourceGroup;

    private PodRebootService service;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        service = new PodRebootService(requestRepository, podService, podMigrationService,
                transactionManager, alarmService, rebootExecutor);

        // rebootExecutor는 제출된 작업을 그 자리에서 동기 실행한다 — 유닛 테스트에는 별도
        // 스레드가 필요 없고, 이렇게 해야 rebootPod() 호출 직후 바로 부수효과를 검증할 수 있다.
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return null;
        }).when(rebootExecutor).execute(any());

        when(mockUser.getUserId()).thenReturn(OWNER_ID);
        when(mockUser.getEmail()).thenReturn("owner@dgu.ac.kr");
        when(mockUser.getName()).thenReturn("테스트유저");
        when(mockImage.getImageId()).thenReturn(1L);
        when(mockImage.getImageName()).thenReturn("cuda");
        when(mockResourceGroup.getRsgroupId()).thenReturn(1);
        when(mockResourceGroup.getServerName()).thenReturn("FARM");

        when(mockRequest.getRequestId()).thenReturn(REQUEST_ID);
        when(mockRequest.getUser()).thenReturn(mockUser);
        when(mockRequest.getContainerImage()).thenReturn(mockImage);
        when(mockRequest.getResourceGroup()).thenReturn(mockResourceGroup);
        when(mockRequest.getRequestGroups()).thenReturn(new LinkedHashSet<>());
        when(mockRequest.getUbuntuUsername()).thenReturn("testuser");
        when(mockRequest.getNodeName()).thenReturn("farm1");
        when(mockRequest.getPodName()).thenReturn("ailab-testuser-1");
        when(requestRepository.findByIdForUpdate(REQUEST_ID)).thenReturn(Optional.of(mockRequest));
        when(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(mockRequest));
    }

    /**
     * mockRequest는 Mockito mock이라 beginReboot()의 실제 상태 검증이 실행되지 않으므로,
     * FULFILLED가 아닌 상태를 재현할 때는 예외를 던지도록 명시적으로 스텁한다.
     */
    private void stubStatus(Status status) {
        when(mockRequest.getStatus()).thenReturn(status);
        if (status != Status.FULFILLED) {
            doThrow(new BusinessException("컨테이너가 실행 중일 때만 재시작할 수 있습니다. 이미 다른 작업이 진행 중입니다.",
                    ErrorCode.INVALID_REQUEST_STATUS)).when(mockRequest).beginReboot();
        }
    }

    private MigratePodResponseDTO migratedResponse() {
        return new MigratePodResponseDTO(
                "migrated", null, "farm1", "farm1", "ailab-testuser-2",
                List.of(new CreatePodResponseDTO.PortInfo("ssh", 22, 30099)),
                null, null, null, null, null
        );
    }

    @Nested
    @DisplayName("정상 케이스")
    class Success {

        @Test
        @DisplayName("현재 노드를 후보로 고정하고 same_node=true로 마이그레이션을 호출한다")
        void rebootPod_callsMigrateOnSameNode() {
            stubStatus(Status.FULFILLED);
            when(podService.migratePod(anyString(), anyList(), any(), anyBoolean())).thenReturn(migratedResponse());

            service.rebootPod(REQUEST_ID, OWNER_ID);

            verify(podService).migratePod("testuser", List.of("farm1"),
                    PodMigrationService.FORCE_MIGRATION_RATIO, true);
            // 기존 Pod 삭제는 config-server가 /migrate 내부에서 처리한다 — 직접 지우면 안 된다.
            verify(podService, never()).deletePod(anyString());
        }

        @Test
        @DisplayName("성공하면 새 Pod 정보를 반영하고 REBOOTING을 FULFILLED로 되돌린다")
        void rebootPod_success_appliesPodInfoAndEndsReboot() {
            stubStatus(Status.FULFILLED);
            MigratePodResponseDTO response = migratedResponse();
            when(podService.migratePod(anyString(), anyList(), any(), anyBoolean())).thenReturn(response);

            service.rebootPod(REQUEST_ID, OWNER_ID);

            verify(mockRequest).beginReboot();
            verify(podMigrationService).applyMigratedPodInfo(REQUEST_ID, mockRequest, response);
            verify(mockRequest).endReboot();
        }

        @Test
        @DisplayName("즉시 응답 DTO는 REBOOTING 상태를 담아 반환된다")
        void rebootPod_returnsRebootingSnapshot() {
            // 즉시 응답은 beginReboot() 직후(같은 트랜잭션)에 만들어지므로 REBOOTING이 담긴다.
            when(mockRequest.getStatus()).thenReturn(Status.REBOOTING);
            when(podService.migratePod(anyString(), anyList(), any(), anyBoolean())).thenReturn(migratedResponse());

            SaveRequestResponseDTO result = service.rebootPod(REQUEST_ID, OWNER_ID);

            assertThat(result.requestId()).isEqualTo(REQUEST_ID);
            assertThat(result.status()).isEqualTo(Status.REBOOTING);
            assertThat(result.ubuntuUsername()).isEqualTo("testuser");
        }

        @Test
        @DisplayName("config-server가 재배치를 건너뛰면 DB는 그대로 두고 상태만 되돌린다")
        void rebootPod_skipped_doesNotTouchPodInfo() {
            stubStatus(Status.FULFILLED);
            MigratePodResponseDTO skipped = new MigratePodResponseDTO(
                    "skipped", "no_capacity", null, null, null, null, null, "farm1", 1.5, "farm1", 1.5);
            when(podService.migratePod(anyString(), anyList(), any(), anyBoolean())).thenReturn(skipped);

            service.rebootPod(REQUEST_ID, OWNER_ID);

            verify(podMigrationService, never()).applyMigratedPodInfo(any(), any(), any());
            verify(mockRequest).endReboot();
        }
    }

    @Nested
    @DisplayName("권한/상태 검증")
    class Validation {

        @Test
        @DisplayName("본인 소유가 아닌 신청이면 거부하고 아무 작업도 제출하지 않는다")
        void rebootPod_notOwner_throwsForbidden() {
            stubStatus(Status.FULFILLED);

            assertThatThrownBy(() -> service.rebootPod(REQUEST_ID, 999L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN_REQUEST);

            verify(mockRequest, never()).beginReboot();
            verify(rebootExecutor, never()).execute(any());
        }

        @Test
        @DisplayName("FULFILLED가 아니면 executor 제출 전에 거부된다")
        void rebootPod_notFulfilled_rejectedBeforeSubmit() {
            stubStatus(Status.PENDING);

            assertThatThrownBy(() -> service.rebootPod(REQUEST_ID, OWNER_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REQUEST_STATUS);

            verify(rebootExecutor, never()).execute(any());
            verifyNoInteractions(podService);
        }

        @Test
        @DisplayName("이미 마이그레이션 중이면 재시작을 거부한다")
        void rebootPod_alreadyMigrating_rejected() {
            stubStatus(Status.MIGRATING);

            assertThatThrownBy(() -> service.rebootPod(REQUEST_ID, OWNER_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REQUEST_STATUS);

            verify(rebootExecutor, never()).execute(any());
        }

        @Test
        @DisplayName("배치된 노드 정보가 없으면 임의 노드로 옮겨가지 않도록 거부한다")
        void rebootPod_noNodeName_rejected() {
            stubStatus(Status.FULFILLED);
            when(mockRequest.getNodeName()).thenReturn(null);

            assertThatThrownBy(() -> service.rebootPod(REQUEST_ID, OWNER_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.POD_NODE_NOT_ASSIGNED);

            verify(mockRequest, never()).beginReboot();
            verify(rebootExecutor, never()).execute(any());
        }

        @Test
        @DisplayName("존재하지 않는 신청이면 404로 실패한다")
        void rebootPod_notFound() {
            when(requestRepository.findByIdForUpdate(REQUEST_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.rebootPod(REQUEST_ID, OWNER_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("실패 처리")
    class Failure {

        @Test
        @DisplayName("executor가 가득 차면 상태를 FULFILLED로 되돌리고 429로 실패한다")
        void rebootPod_executorRejected_revertsAndThrows() {
            stubStatus(Status.FULFILLED);
            doThrow(new TaskRejectedException("pool full")).when(rebootExecutor).execute(any());
            // 복구 트랜잭션은 REBOOTING인 요청만 되돌린다 (제출 거부 시점의 실제 상태)
            when(mockRequest.getStatus()).thenReturn(Status.REBOOTING);

            assertThatThrownBy(() -> service.rebootPod(REQUEST_ID, OWNER_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.POD_REBOOT_CONCURRENCY_LIMIT);

            verify(mockRequest).endReboot();
            verifyNoInteractions(podService);
        }

        @Test
        @DisplayName("마이그레이션 호출이 실패하면 REBOOTING에 갇히지 않고 FULFILLED로 되돌린다")
        void rebootPod_migrateFails_revertsToFulfilled() {
            when(mockRequest.getStatus()).thenReturn(Status.FULFILLED, Status.REBOOTING);
            when(podService.migratePod(anyString(), anyList(), any(), anyBoolean()))
                    .thenThrow(new BusinessException(ErrorCode.POD_MIGRATION_FAILED));

            // 비동기 본체의 실패는 호출자에게 전파되지 않는다 — 이미 즉시 응답을 반환한 뒤다.
            SaveRequestResponseDTO result = service.rebootPod(REQUEST_ID, OWNER_ID);

            assertThat(result).isNotNull();
            verify(mockRequest).endReboot();
            // 기존 Pod는 config-server가 지우기 전이라 그대로 살아있다 — 보상 삭제를 하면 안 된다.
            verify(podService, never()).deletePod(anyString());
            verify(podMigrationService, never()).applyMigratedPodInfo(any(), any(), any());
        }

        @Test
        @DisplayName("결과 DB 반영이 실패하면 REBOOTING을 유지한 채 관리자 알림을 보낸다")
        void rebootPod_dbApplyFails_keepsRebootingAndAlerts() {
            stubStatus(Status.FULFILLED);
            when(podService.migratePod(anyString(), anyList(), any(), anyBoolean())).thenReturn(migratedResponse());
            doThrow(new IllegalStateException("db down"))
                    .when(podMigrationService).applyMigratedPodInfo(any(), any(), any());

            service.rebootPod(REQUEST_ID, OWNER_ID);

            // 새 Pod는 이미 떴고 기존 Pod는 지워진 뒤라 FULFILLED로 되돌리면 DB와 실제가 어긋난다.
            verify(mockRequest, never()).endReboot();
            verify(alarmService).sendSlackAlert(contains("결과 DB 반영 실패"), eq(null));
        }
    }
}
