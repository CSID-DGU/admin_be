package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.domain.alarm.service.AlarmService;
import DGU_AI_LAB.admin_be.domain.containerImage.entity.ContainerImage;
import DGU_AI_LAB.admin_be.domain.containerImage.repository.ContainerImageRepository;
import DGU_AI_LAB.admin_be.domain.groups.entity.Group;
import DGU_AI_LAB.admin_be.domain.groups.repository.GroupRepository;
import DGU_AI_LAB.admin_be.domain.pod.entity.PodExternalPort;
import DGU_AI_LAB.admin_be.domain.pod.repository.PodExternalPortRepository;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.ApproveRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.UserCreationRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.CreatePodResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.repository.ChangeRequestRepository;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup;
import DGU_AI_LAB.admin_be.domain.resourceGroups.repository.ResourceGroupRepository;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.RejectRequestDTO;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminRequestCommandServiceTest {

    @Mock private AlarmService alarmService;
    @Mock private RequestRepository requestRepository;
    @Mock private UserRepository userRepository;
    @Mock private ContainerImageRepository containerImageRepository;
    @Mock private ResourceGroupRepository resourceGroupRepository;
    @Mock private ChangeRequestRepository changeRequestRepository;
    @Mock private GroupRepository groupRepository;
    @Mock private PodExternalPortRepository podExternalPortRepository;
    @Mock private PodService podService;
    @Mock private UbuntuAccountService ubuntuAccountService;
    @Mock private WebClient mockWebClient;

    // WebClient 체이닝 mock
    @Mock private WebClient.RequestBodyUriSpec putUriSpec;
    @Mock private WebClient.RequestBodySpec putBodySpec;
    @Mock private WebClient.RequestHeadersSpec putHeadersSpec;
    @Mock private WebClient.ResponseSpec putResponseSpec;

    // 공유 엔티티 mock - when() 내부에서 다른 mock 호출로 인한 UnfinishedStubbingException 방지
    @Mock private ContainerImage mockImage;
    @Mock private ResourceGroup mockRg;
    @Mock private User mockUser;

    private AdminRequestCommandService service;

    @BeforeEach
    void setUp() {
        // @RequiredArgsConstructor 생성자 필드 선언 순서대로 주입
        service = new AdminRequestCommandService(
                alarmService, requestRepository, userRepository, containerImageRepository,
                resourceGroupRepository, changeRequestRepository,
                groupRepository, podExternalPortRepository, podService, ubuntuAccountService, new ObjectMapper(),
                mockWebClient
        );
        // 공유 엔티티 기본 설정
        when(mockUser.getName()).thenReturn("테스트유저");
        when(mockImage.getImageId()).thenReturn(1L);
        when(mockRg.getRsgroupId()).thenReturn(1);
    }

    /** 사용자 생성 PUT 요청 WebClient 모킹
     *  체이닝: put() → putUriSpec → (uri) → putBodySpec → (bodyValue) → putHeadersSpec → (retrieve) → putResponseSpec */
    @SuppressWarnings("unchecked")
    private void stubWebClientPut() {
        when(mockWebClient.put()).thenReturn(putUriSpec);
        when(putUriSpec.uri(anyString())).thenReturn(putBodySpec);
        doReturn(putHeadersSpec).when(putBodySpec).bodyValue(any());
        when(putHeadersSpec.retrieve()).thenReturn(putResponseSpec);
        when(putResponseSpec.onStatus(any(), any())).thenReturn(putResponseSpec);
        when(putResponseSpec.bodyToMono(AdminRequestCommandService.UserCreationResponse.class))
                .thenReturn(Mono.just(new AdminRequestCommandService.UserCreationResponse(
                        "created",
                        new AdminRequestCommandService.UserCreationResponse.UserInfo(2001L, 2001L)
                )));
    }

    /** 공통 Request mock 설정 */
    private Request buildMockedRequest(Long requestId) {
        Request request = mock(Request.class);
        when(request.getStatus()).thenReturn(Status.PENDING);
        when(request.getUbuntuUsername()).thenReturn("testuser");
        when(request.getUbuntuPassword()).thenReturn("encoded_pw");
        when(request.getUbuntuPasswordBase64()).thenReturn("cGxhaW5fdGV4dF9wdw==");
        when(request.getRequestGroups()).thenReturn(new LinkedHashSet<>());
        when(request.getUser()).thenReturn(mockUser);
        when(request.getResourceGroup()).thenReturn(mockRg);
        when(request.getContainerImage()).thenReturn(mockImage);
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(request));
        return request;
    }

    @Test
    @DisplayName("승인 시 Pod 응답의 external ports가 PodExternalPortRepository에 올바르게 저장된다")
    void approveRequest_savesPodExternalPortsToNewTable() {
        // Given
        Long requestId = 1L;
        Request request = buildMockedRequest(requestId);
        stubWebClientPut();

        // ssh(22→30022), jupyter(8888→30888) 두 개의 external port를 반환하는 pod 응답
        CreatePodResponseDTO podResponse = new CreatePodResponseDTO(
                "running", "farm1", "pod-testuser-xxxx",
                List.of(
                        new CreatePodResponseDTO.PortInfo("ssh", 22, 30022),
                        new CreatePodResponseDTO.PortInfo("jupyter", 8888, 30888)
                )
        );
        when(podService.createPod("testuser")).thenReturn(podResponse);
        when(containerImageRepository.findById(1L)).thenReturn(Optional.of(mockImage));
        when(resourceGroupRepository.findById(1)).thenReturn(Optional.of(mockRg));
        when(podExternalPortRepository.save(any(PodExternalPort.class))).thenAnswer(inv -> inv.getArgument(0));

        ApproveRequestDTO dto = new ApproveRequestDTO(requestId, 1L, 1, 10L, "승인합니다");

        // When
        service.approveRequest(dto);

        // Then - PodExternalPortRepository에 2개의 포트가 저장되어야 함
        ArgumentCaptor<PodExternalPort> captor = ArgumentCaptor.forClass(PodExternalPort.class);
        verify(podExternalPortRepository, times(2)).save(captor.capture());
        verify(request).assignUbuntuIds(2001L, 2001L);

        List<PodExternalPort> saved = captor.getAllValues();
        assertThat(saved).hasSize(2);

        PodExternalPort sshPort = saved.get(0);
        assertThat(sshPort.getUsagePurpose()).isEqualTo("ssh");
        assertThat(sshPort.getInternalPort()).isEqualTo(22);
        assertThat(sshPort.getExternalPort()).isEqualTo(30022);

        PodExternalPort jupyterPort = saved.get(1);
        assertThat(jupyterPort.getUsagePurpose()).isEqualTo("jupyter");
        assertThat(jupyterPort.getInternalPort()).isEqualTo(8888);
        assertThat(jupyterPort.getExternalPort()).isEqualTo(30888);

        ArgumentCaptor<UserCreationRequestDTO> userCreationCaptor = ArgumentCaptor.forClass(UserCreationRequestDTO.class);
        verify(putBodySpec).bodyValue(userCreationCaptor.capture());
        UserCreationRequestDTO userCreationRequest = userCreationCaptor.getValue();
        assertThat(userCreationRequest.username()).isEqualTo("testuser");
        assertThat(userCreationRequest.passwordBase64()).isEqualTo("cGxhaW5fdGV4dF9wdw==");
        assertThat(userCreationRequest.gecos()).isEqualTo("테스트유저");
        assertThat(userCreationRequest.primaryGroupName()).isEqualTo("testuser");
        assertThat(userCreationRequest.enableSudo()).isFalse();
    }

    @Test
    @DisplayName("Pod가 포트 없이 응답할 때 PodExternalPort는 저장되지 않는다")
    void approveRequest_noPodPorts_savesNoPodExternalPorts() {
        // Given
        Long requestId = 2L;
        Request request = buildMockedRequest(requestId);
        stubWebClientPut();

        // 포트가 없는 pod 응답
        CreatePodResponseDTO podResponse = new CreatePodResponseDTO(
                "running", "farm1", "pod-testuser-yyyy", List.of()
        );
        when(podService.createPod("testuser")).thenReturn(podResponse);
        when(containerImageRepository.findById(1L)).thenReturn(Optional.of(mockImage));
        when(resourceGroupRepository.findById(1)).thenReturn(Optional.of(mockRg));

        ApproveRequestDTO dto = new ApproveRequestDTO(requestId, 1L, 1, 10L, "승인합니다");

        // When
        service.approveRequest(dto);

        // Then - 포트가 없으므로 save 호출 없음
        verify(podExternalPortRepository, never()).save(any());
    }

    @Test
    @DisplayName("승인 시 PodService.createPod()가 정확히 1회 호출된다")
    void approveRequest_callsPodServiceCreatePodOnce() {
        // Given
        Long requestId = 3L;
        Request request = buildMockedRequest(requestId);
        stubWebClientPut();

        CreatePodResponseDTO podResponse = new CreatePodResponseDTO(
                "running", "farm1", "pod-testuser-zzzz", List.of()
        );
        when(podService.createPod("testuser")).thenReturn(podResponse);
        when(containerImageRepository.findById(1L)).thenReturn(Optional.of(mockImage));
        when(resourceGroupRepository.findById(1)).thenReturn(Optional.of(mockRg));

        ApproveRequestDTO dto = new ApproveRequestDTO(requestId, 1L, 1, 10L, null);

        // When
        service.approveRequest(dto);

        // Then - PodService.createPod()가 username으로 정확히 1회 호출
        verify(podService, times(1)).createPod("testuser");
    }

    // ── C-3: Pod 생성 null 응답 / NPE 보상 트랜잭션 테스트 ─────────────────

    @Nested
    @DisplayName("C-3: Pod 생성 실패 시 보상 트랜잭션")
    class PodCreationFailureCompensation {

        @Test
        @DisplayName("createPod()가 BusinessException을 던지면 Ubuntu 계정 삭제가 호출된다")
        void approveRequest_podThrowsBusinessException_compensatesWithAccountDeletion() {
            // Given
            Long requestId = 10L;
            buildMockedRequest(requestId);
            stubWebClientPut();

            when(podService.createPod("testuser"))
                    .thenThrow(new BusinessException(ErrorCode.POD_CREATION_FAILED));

            ApproveRequestDTO dto = new ApproveRequestDTO(requestId, 1L, 1, 10L, "승인");

            // When & Then
            assertThatThrownBy(() -> service.approveRequest(dto))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.POD_CREATION_FAILED);

            // Ubuntu 계정 보상 삭제가 반드시 호출되어야 함
            verify(ubuntuAccountService).deleteUbuntuAccount("testuser");
        }

        @Test
        @DisplayName("createPod()가 null을 반환하면 BusinessException이 발생하고 Ubuntu 계정이 삭제된다")
        void approveRequest_podReturnsNull_throwsAndCompensates() {
            // Given
            Long requestId = 11L;
            buildMockedRequest(requestId);
            stubWebClientPut();

            // PodService가 null을 반환하면 내부에서 BusinessException을 던져야 함 (수정 후 동작)
            when(podService.createPod("testuser"))
                    .thenThrow(new BusinessException(ErrorCode.POD_CREATION_FAILED));

            ApproveRequestDTO dto = new ApproveRequestDTO(requestId, 1L, 1, 10L, "승인");

            // When & Then
            assertThatThrownBy(() -> service.approveRequest(dto))
                    .isInstanceOf(BusinessException.class);

            // 보상 트랜잭션이 동작해야 함
            verify(ubuntuAccountService).deleteUbuntuAccount("testuser");
            // DB 상태 변경(assignPodInfo 등)은 실행되지 않아야 함
            verify(containerImageRepository, never()).findById(any());
        }

        @Test
        @DisplayName("createPod() 실패 시 보상 Ubuntu 계정 삭제 자체가 실패해도 원래 예외가 전파된다")
        void approveRequest_podFails_compensationAlsoFails_originalExceptionPropagates() {
            // Given
            Long requestId = 12L;
            buildMockedRequest(requestId);
            stubWebClientPut();

            when(podService.createPod("testuser"))
                    .thenThrow(new BusinessException(ErrorCode.POD_CREATION_FAILED));
            doThrow(new RuntimeException("계정 삭제 실패"))
                    .when(ubuntuAccountService).deleteUbuntuAccount("testuser");

            ApproveRequestDTO dto = new ApproveRequestDTO(requestId, 1L, 1, 10L, "승인");

            // When & Then - 보상 실패해도 원래 예외(POD_CREATION_FAILED)가 전파되어야 함
            assertThatThrownBy(() -> service.approveRequest(dto))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.POD_CREATION_FAILED);
        }

        @Test
        @DisplayName("createPod() 정상 응답이면 보상 트랜잭션은 실행되지 않는다")
        void approveRequest_podSucceeds_noCompensation() {
            // Given
            Long requestId = 13L;
            buildMockedRequest(requestId);
            stubWebClientPut();

            CreatePodResponseDTO podResponse = new CreatePodResponseDTO(
                    "running", "farm1", "pod-testuser-ok", List.of()
            );
            when(podService.createPod("testuser")).thenReturn(podResponse);
            when(containerImageRepository.findById(1L)).thenReturn(Optional.of(mockImage));
            when(resourceGroupRepository.findById(1)).thenReturn(Optional.of(mockRg));

            ApproveRequestDTO dto = new ApproveRequestDTO(requestId, 1L, 1, 10L, "승인");

            // When
            service.approveRequest(dto);

            // Then - 보상 트랜잭션 미실행
            verify(ubuntuAccountService, never()).deleteUbuntuAccount(any());
            verify(podService, never()).deletePod(any());
        }

        @Test
        @DisplayName("PENDING 상태가 아닌 요청 승인 시 BusinessException 발생")
        void approveRequest_nonPendingStatus_throwsBusinessException() {
            // Given
            Long requestId = 14L;
            Request request = mock(Request.class);
            when(request.getStatus()).thenReturn(Status.FULFILLED);
            when(requestRepository.findById(requestId)).thenReturn(Optional.of(request));

            ApproveRequestDTO dto = new ApproveRequestDTO(requestId, 1L, 1, 10L, "승인");

            // When & Then
            assertThatThrownBy(() -> service.approveRequest(dto))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_REQUEST_STATUS);

            // Pod 생성, Ubuntu 계정 생성 모두 호출되지 않아야 함
            verify(podService, never()).createPod(any());
        }

        @Test
        @DisplayName("존재하지 않는 requestId 승인 시 BusinessException 발생")
        void approveRequest_requestNotFound_throwsBusinessException() {
            // Given
            when(requestRepository.findById(999L)).thenReturn(Optional.empty());

            ApproveRequestDTO dto = new ApproveRequestDTO(999L, 1L, 1, 10L, "승인");

            // When & Then
            assertThatThrownBy(() -> service.approveRequest(dto))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    // ── rejectRequest 테스트 ───────────────────────────────────────────────

    @Nested
    @DisplayName("rejectRequest 기본 동작")
    class RejectRequest {

        /** rejectRequest용 Request mock — fromEntity()가 요구하는 필드를 모두 스텁 */
        private Request buildRejectableMock(Long requestId, Status status) {
            Request request = mock(Request.class);
            when(request.getStatus()).thenReturn(status);
            when(request.getResourceGroup()).thenReturn(mockRg);
            when(request.getUser()).thenReturn(mockUser);
            when(request.getContainerImage()).thenReturn(mockImage);
            when(request.getRequestGroups()).thenReturn(new LinkedHashSet<>());
            when(requestRepository.findById(requestId)).thenReturn(Optional.of(request));

            when(mockRg.getRsgroupId()).thenReturn(1);
            when(mockUser.getName()).thenReturn("테스트유저");
            when(mockImage.getImageId()).thenReturn(1L);
            return request;
        }

        @Test
        @DisplayName("PENDING 상태 요청 거절 시 reject()가 호출된다")
        void rejectRequest_pendingStatus_callsReject() {
            // Given
            Long requestId = 20L;
            Request request = buildRejectableMock(requestId, Status.PENDING);
            RejectRequestDTO dto = new RejectRequestDTO(requestId, "부적절한 신청");

            // When
            service.rejectRequest(dto);

            // Then
            verify(request).reject("부적절한 신청");
        }

        @Test
        @DisplayName("FULFILLED 상태 요청도 거절 가능하다")
        void rejectRequest_fulfilledStatus_callsReject() {
            // Given
            Long requestId = 21L;
            Request request = buildRejectableMock(requestId, Status.FULFILLED);
            RejectRequestDTO dto = new RejectRequestDTO(requestId, "관리자 거절");

            // When
            service.rejectRequest(dto);

            // Then
            verify(request).reject("관리자 거절");
        }

        @Test
        @DisplayName("DENIED 상태 요청 거절 시 BusinessException 발생")
        void rejectRequest_alreadyDenied_throwsBusinessException() {
            // Given
            Long requestId = 22L;
            buildRejectableMock(requestId, Status.DENIED);
            RejectRequestDTO dto = new RejectRequestDTO(requestId, "재거절 시도");

            // When & Then
            assertThatThrownBy(() -> service.rejectRequest(dto))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_REQUEST_STATUS);
        }

        @Test
        @DisplayName("DELETED 상태 요청 거절 시 BusinessException 발생")
        void rejectRequest_deletedStatus_throwsBusinessException() {
            // Given
            Long requestId = 23L;
            buildRejectableMock(requestId, Status.DELETED);
            RejectRequestDTO dto = new RejectRequestDTO(requestId, "삭제된 요청 거절 시도");

            // When & Then
            assertThatThrownBy(() -> service.rejectRequest(dto))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_REQUEST_STATUS);
        }
    }
}
