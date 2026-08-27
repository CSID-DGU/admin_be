package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.domain.pod.entity.PodExternalPort;
import DGU_AI_LAB.admin_be.domain.pod.repository.PodExternalPortRepository;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.MigratePodRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.CreatePodResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.MigratePodResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PodMigrationService")
class PodMigrationServiceTest {

    @Mock private RequestRepository requestRepository;
    @Mock private PodExternalPortRepository podExternalPortRepository;
    @Mock private PodService podService;
    @Mock private PlatformTransactionManager transactionManager;
    @Mock private TransactionStatus transactionStatus;
    @Mock private Request mockRequest;

    private PodMigrationService service;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        service = new PodMigrationService(requestRepository, podExternalPortRepository, podService, transactionManager);
    }

    private void stubExistingRequest(Long requestId, Status status) {
        when(mockRequest.getStatus()).thenReturn(status);
        when(mockRequest.getUbuntuUsername()).thenReturn("testuser");
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(mockRequest));
    }

    @Nested
    @DisplayName("정상 케이스")
    class Success {

        @Test
        @DisplayName("migrated 응답이면 assignPodInfo와 포트 재저장이 호출된다")
        void migratePod_migrated_updatesRequestAndReplacesPorts() {
            Long requestId = 1L;
            stubExistingRequest(requestId, Status.FULFILLED);

            MigratePodResponseDTO response = new MigratePodResponseDTO(
                    "migrated", null, "farm1", "farm2", "pod-testuser-2",
                    List.of(
                            new CreatePodResponseDTO.PortInfo("ssh", 22, 30099),
                            new CreatePodResponseDTO.PortInfo("jupyter", 8888, 30988)
                    ),
                    null, null, null, null
            );
            when(podService.migratePod("testuser", List.of("farm1", "farm2"), 0.2)).thenReturn(response);
            when(podExternalPortRepository.save(any(PodExternalPort.class))).thenAnswer(inv -> inv.getArgument(0));

            MigratePodRequestDTO dto = new MigratePodRequestDTO(List.of("farm1", "farm2"), 0.2);
            MigratePodResponseDTO result = service.migratePod(requestId, dto);

            assertThat(result).isEqualTo(response);
            verify(mockRequest).assignPodInfo("pod-testuser-2", "farm2");
            verify(podExternalPortRepository).deleteByRequestRequestId(requestId);

            ArgumentCaptor<PodExternalPort> captor = ArgumentCaptor.forClass(PodExternalPort.class);
            verify(podExternalPortRepository, times(2)).save(captor.capture());
            assertThat(captor.getAllValues())
                    .extracting(PodExternalPort::getUsagePurpose)
                    .containsExactly("ssh", "jupyter");
        }

        @Test
        @DisplayName("skipped 응답이면 DB를 건드리지 않고 그대로 반환한다")
        void migratePod_skipped_doesNotTouchDb() {
            Long requestId = 2L;
            stubExistingRequest(requestId, Status.FULFILLED);

            MigratePodResponseDTO response = new MigratePodResponseDTO(
                    "skipped", "no_significant_improvement", null, null, null, null,
                    "farm1", 1.5, "farm2", 1.4
            );
            when(podService.migratePod(eq("testuser"), any(), any())).thenReturn(response);

            MigratePodRequestDTO dto = new MigratePodRequestDTO(List.of("farm1", "farm2"), null);
            MigratePodResponseDTO result = service.migratePod(requestId, dto);

            assertThat(result.isMigrated()).isFalse();
            assertThat(result.reason()).isEqualTo("no_significant_improvement");
            verify(mockRequest, never()).assignPodInfo(any(), any());
            verify(podExternalPortRepository, never()).deleteByRequestRequestId(any());
            verify(podExternalPortRepository, never()).save(any());
        }

        @Test
        @DisplayName("min_improvement_ratio를 생략하면 null로 config-server에 그대로 전달된다")
        void migratePod_passesNullRatio_whenOmitted() {
            Long requestId = 3L;
            stubExistingRequest(requestId, Status.FULFILLED);
            when(podService.migratePod(any(), any(), any())).thenReturn(
                    new MigratePodResponseDTO("skipped", "no_candidate_node", null, null, null, null, null, null, null, null)
            );

            service.migratePod(requestId, new MigratePodRequestDTO(List.of("farm1"), null));

            verify(podService).migratePod("testuser", List.of("farm1"), null);
        }
    }

    @Nested
    @DisplayName("실패 케이스")
    class Failure {

        @Test
        @DisplayName("존재하지 않는 requestId면 RESOURCE_NOT_FOUND 예외가 발생하고 외부 API를 호출하지 않는다")
        void migratePod_requestNotFound_throwsAndSkipsExternalCall() {
            Long requestId = 99L;
            when(requestRepository.findById(requestId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.migratePod(requestId, new MigratePodRequestDTO(List.of("farm1"), null)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);

            verify(podService, never()).migratePod(any(), any(), any());
        }

        @Test
        @DisplayName("FULFILLED 상태가 아니면 INVALID_REQUEST_STATUS 예외가 발생하고 외부 API를 호출하지 않는다")
        void migratePod_notFulfilled_throwsAndSkipsExternalCall() {
            Long requestId = 4L;
            stubExistingRequest(requestId, Status.PENDING);

            assertThatThrownBy(() -> service.migratePod(requestId, new MigratePodRequestDTO(List.of("farm1"), null)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_REQUEST_STATUS);

            verify(podService, never()).migratePod(any(), any(), any());
        }

        @Test
        @DisplayName("config-server 호출이 BusinessException을 던지면 DB를 건드리지 않고 그대로 전파한다")
        void migratePod_externalCallFails_propagatesAndSkipsDbUpdate() {
            Long requestId = 5L;
            stubExistingRequest(requestId, Status.FULFILLED);
            when(podService.migratePod(any(), any(), any()))
                    .thenThrow(new BusinessException("Pod 마이그레이션 실패", ErrorCode.POD_MIGRATION_FAILED));

            assertThatThrownBy(() -> service.migratePod(requestId, new MigratePodRequestDTO(List.of("farm1"), null)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.POD_MIGRATION_FAILED);

            verify(mockRequest, never()).assignPodInfo(any(), any());
            verify(podExternalPortRepository, never()).deleteByRequestRequestId(any());
        }

        @Test
        @DisplayName("migrated 응답인데 ports가 비어있으면 기존 포트만 삭제하고 새로 저장하지 않는다")
        void migratePod_migratedWithEmptyPorts_deletesOldPortsOnly() {
            Long requestId = 6L;
            stubExistingRequest(requestId, Status.FULFILLED);
            MigratePodResponseDTO response = new MigratePodResponseDTO(
                    "migrated", null, "farm1", "farm2", "pod-testuser-3", List.of(),
                    null, null, null, null
            );
            when(podService.migratePod(any(), any(), any())).thenReturn(response);

            service.migratePod(requestId, new MigratePodRequestDTO(List.of("farm1", "farm2"), null));

            verify(podExternalPortRepository).deleteByRequestRequestId(requestId);
            verify(podExternalPortRepository, never()).save(any());
        }
    }
}
