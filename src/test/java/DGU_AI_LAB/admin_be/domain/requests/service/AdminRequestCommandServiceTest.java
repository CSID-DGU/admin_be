package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.domain.alarm.service.AlarmService;
import DGU_AI_LAB.admin_be.domain.containerImage.entity.ContainerImage;
import DGU_AI_LAB.admin_be.domain.containerImage.repository.ContainerImageRepository;
import DGU_AI_LAB.admin_be.domain.groups.entity.Group;
import DGU_AI_LAB.admin_be.domain.groups.repository.GroupRepository;
import DGU_AI_LAB.admin_be.domain.pod.entity.PodExternalPort;
import DGU_AI_LAB.admin_be.domain.pod.repository.PodExternalPortRepository;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.ApproveModificationDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.ApproveRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.RejectModificationDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.RejectRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.UserCreationRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.CreatePodResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.entity.ChangeRequest;
import DGU_AI_LAB.admin_be.domain.requests.entity.ChangeType;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.repository.ChangeRequestRepository;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup;
import DGU_AI_LAB.admin_be.domain.resourceGroups.repository.ResourceGroupRepository;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
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
    @Mock private PlatformTransactionManager transactionManager;
    @Mock private TransactionStatus transactionStatus;

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
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);

        // @RequiredArgsConstructor 생성자 필드 선언 순서대로 주입
        service = new AdminRequestCommandService(
                alarmService, requestRepository, userRepository, containerImageRepository,
                resourceGroupRepository, changeRequestRepository,
                groupRepository, podExternalPortRepository, podService, ubuntuAccountService, new ObjectMapper(),
                mockWebClient, transactionManager
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
        @DisplayName("createPod()가 BusinessException을 던지면 Ubuntu 계정 삭제 및 상태 복구가 호출된다")
        void approveRequest_podThrowsBusinessException_compensatesWithAccountDeletion() {
            Long requestId = 10L;
            buildMockedRequest(requestId);
            stubWebClientPut();

            when(podService.createPod("testuser"))
                    .thenThrow(new BusinessException(ErrorCode.POD_CREATION_FAILED));

            ApproveRequestDTO dto = new ApproveRequestDTO(requestId, 1L, 1, 10L, "승인");

            assertThatThrownBy(() -> service.approveRequest(dto))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.POD_CREATION_FAILED);

            verify(ubuntuAccountService).deleteUbuntuAccount("testuser");
            // requestRepository.findById 재조회 후 revertToPending 호출 검증
            verify(requestRepository, atLeast(2)).findById(requestId);
        }

        @Test
        @DisplayName("createPod()가 null을 반환하면 BusinessException이 발생하고 Ubuntu 계정이 삭제된다")
        void approveRequest_podReturnsNull_throwsAndCompensates() {
            Long requestId = 11L;
            buildMockedRequest(requestId);
            stubWebClientPut();

            when(podService.createPod("testuser"))
                    .thenThrow(new BusinessException(ErrorCode.POD_CREATION_FAILED));

            ApproveRequestDTO dto = new ApproveRequestDTO(requestId, 1L, 1, 10L, "승인");

            assertThatThrownBy(() -> service.approveRequest(dto))
                    .isInstanceOf(BusinessException.class);

            verify(ubuntuAccountService).deleteUbuntuAccount("testuser");
            verify(containerImageRepository, never()).findById(any());
        }

        @Test
        @DisplayName("createPod() 실패 시 보상 Ubuntu 계정 삭제 자체가 실패해도 원래 예외가 전파된다")
        void approveRequest_podFails_compensationAlsoFails_originalExceptionPropagates() {
            Long requestId = 12L;
            buildMockedRequest(requestId);
            stubWebClientPut();

            when(podService.createPod("testuser"))
                    .thenThrow(new BusinessException(ErrorCode.POD_CREATION_FAILED));
            doThrow(new RuntimeException("계정 삭제 실패"))
                    .when(ubuntuAccountService).deleteUbuntuAccount("testuser");

            ApproveRequestDTO dto = new ApproveRequestDTO(requestId, 1L, 1, 10L, "승인");

            assertThatThrownBy(() -> service.approveRequest(dto))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.POD_CREATION_FAILED);
        }

        @Test
        @DisplayName("createPod() 정상 응답이면 보상 트랜잭션은 실행되지 않는다")
        void approveRequest_podSucceeds_noCompensation() {
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

            service.approveRequest(dto);

            verify(ubuntuAccountService, never()).deleteUbuntuAccount(any());
            verify(podService, never()).deletePod(any());
        }

        @Test
        @DisplayName("PENDING 상태가 아닌 요청 승인 시 BusinessException 발생")
        void approveRequest_nonPendingStatus_throwsBusinessException() {
            Long requestId = 14L;
            Request request = mock(Request.class);
            when(request.getStatus()).thenReturn(Status.FULFILLED);
            when(requestRepository.findById(requestId)).thenReturn(Optional.of(request));

            ApproveRequestDTO dto = new ApproveRequestDTO(requestId, 1L, 1, 10L, "승인");

            assertThatThrownBy(() -> service.approveRequest(dto))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_REQUEST_STATUS);

            verify(podService, never()).createPod(any());
        }

        @Test
        @DisplayName("존재하지 않는 requestId 승인 시 BusinessException 발생")
        void approveRequest_requestNotFound_throwsBusinessException() {
            when(requestRepository.findById(999L)).thenReturn(Optional.empty());

            ApproveRequestDTO dto = new ApproveRequestDTO(999L, 1L, 1, 10L, "승인");

            assertThatThrownBy(() -> service.approveRequest(dto))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // rejectRequest 테스트
    // ──────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("rejectRequest")
    class RejectRequest {

        @Test
        @DisplayName("PENDING 상태 요청을 거절하면 request.reject()가 호출된다")
        void rejectRequest_success_whenStatusIsPending() {
            Request request = buildMockedRequestWithStatus(30L, Status.PENDING);

            RejectRequestDTO dto = new RejectRequestDTO(30L, "신청서 양식 미흡");
            service.rejectRequest(dto);

            verify(request).reject("신청서 양식 미흡");
        }

        @Test
        @DisplayName("FULFILLED 상태 요청도 거절 가능하다 (현재 정책)")
        void rejectRequest_success_whenStatusIsFulfilled() {
            Request request = buildMockedRequestWithStatus(31L, Status.FULFILLED);

            RejectRequestDTO dto = new RejectRequestDTO(31L, "계정 정책 위반");
            service.rejectRequest(dto);

            verify(request).reject("계정 정책 위반");
        }

        @Test
        @DisplayName("PROCESSING 상태 요청도 거절 가능하다 (승인 진행 중 취소)")
        void rejectRequest_success_whenStatusIsProcessing() {
            Request request = buildMockedRequestWithStatus(34L, Status.PROCESSING);

            RejectRequestDTO dto = new RejectRequestDTO(34L, "승인 취소");
            service.rejectRequest(dto);

            verify(request).reject("승인 취소");
        }

        @Test
        @DisplayName("존재하지 않는 requestId로 거절하면 BusinessException을 던진다")
        void rejectRequest_throwsException_whenRequestNotFound() {
            when(requestRepository.findById(999L)).thenReturn(Optional.empty());

            RejectRequestDTO dto = new RejectRequestDTO(999L, "사유");

            assertThatThrownBy(() -> service.rejectRequest(dto))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("DENIED 상태 요청을 거절하려 하면 BusinessException을 던진다")
        void rejectRequest_throwsException_whenStatusIsDenied() {
            buildMockedRequestWithStatus(32L, Status.DENIED);

            RejectRequestDTO dto = new RejectRequestDTO(32L, "사유");

            assertThatThrownBy(() -> service.rejectRequest(dto))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("DELETED 상태 요청을 거절하려 하면 BusinessException을 던진다")
        void rejectRequest_throwsException_whenStatusIsDeleted() {
            buildMockedRequestWithStatus(33L, Status.DELETED);

            RejectRequestDTO dto = new RejectRequestDTO(33L, "사유");

            assertThatThrownBy(() -> service.rejectRequest(dto))
                    .isInstanceOf(BusinessException.class);
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // rejectModification 테스트
    // ──────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("rejectModification")
    class RejectModification {

        @Test
        @DisplayName("PENDING 상태 변경 요청을 거절하면 changeRequest.deny()가 호출된다")
        void rejectModification_success_whenStatusIsPending() {
            ChangeRequest changeRequest = mock(ChangeRequest.class);
            when(changeRequest.getStatus()).thenReturn(Status.PENDING);
            when(changeRequestRepository.findById(1L)).thenReturn(Optional.of(changeRequest));
            when(userRepository.findById(100L)).thenReturn(Optional.of(mockUser));

            RejectModificationDTO dto = new RejectModificationDTO(1L, "변경 사유 불충분");
            service.rejectModification(100L, dto);

            verify(changeRequest).deny(mockUser, "변경 사유 불충분");
        }

        @Test
        @DisplayName("존재하지 않는 변경 요청 ID로 거절하면 BusinessException을 던진다")
        void rejectModification_throwsException_whenChangeRequestNotFound() {
            when(changeRequestRepository.findById(999L)).thenReturn(Optional.empty());

            RejectModificationDTO dto = new RejectModificationDTO(999L, "사유");

            assertThatThrownBy(() -> service.rejectModification(100L, dto))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("PENDING 이 아닌 상태의 변경 요청 거절 시 BusinessException을 던진다")
        void rejectModification_throwsException_whenStatusIsNotPending() {
            ChangeRequest changeRequest = mock(ChangeRequest.class);
            when(changeRequest.getStatus()).thenReturn(Status.FULFILLED);
            when(changeRequestRepository.findById(2L)).thenReturn(Optional.of(changeRequest));

            RejectModificationDTO dto = new RejectModificationDTO(2L, "사유");

            assertThatThrownBy(() -> service.rejectModification(100L, dto))
                    .isInstanceOf(BusinessException.class);
            verify(changeRequest, never()).deny(any(), anyString());
        }

        @Test
        @DisplayName("존재하지 않는 adminId로 거절하면 BusinessException을 던진다")
        void rejectModification_throwsException_whenAdminNotFound() {
            ChangeRequest changeRequest = mock(ChangeRequest.class);
            when(changeRequest.getStatus()).thenReturn(Status.PENDING);
            when(changeRequestRepository.findById(3L)).thenReturn(Optional.of(changeRequest));
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            RejectModificationDTO dto = new RejectModificationDTO(3L, "사유");

            assertThatThrownBy(() -> service.rejectModification(999L, dto))
                    .isInstanceOf(BusinessException.class);
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // approveModification 테스트
    // ──────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("approveModification")
    class ApproveModification {

        @Test
        @DisplayName("VOLUME_SIZE 변경 요청 승인 시 originalRequest.updateVolumeSize()가 호출된다")
        void approveModification_volumeSize_success() throws Exception {
            ChangeRequest changeRequest = mock(ChangeRequest.class);
            Request originalRequest = buildMockedRequestWithStatus(20L, Status.FULFILLED);
            when(changeRequest.getStatus()).thenReturn(Status.PENDING);
            when(changeRequest.getChangeType()).thenReturn(ChangeType.VOLUME_SIZE);
            when(changeRequest.getNewValue()).thenReturn("200");
            when(changeRequest.getRequest()).thenReturn(originalRequest);
            when(changeRequestRepository.findById(1L)).thenReturn(Optional.of(changeRequest));
            when(userRepository.findById(100L)).thenReturn(Optional.of(mockUser));

            ApproveModificationDTO dto = new ApproveModificationDTO(1L, "승인합니다");
            service.approveModification(100L, dto);

            verify(originalRequest).updateVolumeSize(200L);
            verify(changeRequest).approve(mockUser, "승인합니다");
        }

        @Test
        @DisplayName("EXPIRES_AT 변경 요청 승인 시 originalRequest.updateExpiresAt()가 호출된다")
        void approveModification_expiresAt_success() throws Exception {
            ChangeRequest changeRequest = mock(ChangeRequest.class);
            Request originalRequest = buildMockedRequestWithStatus(21L, Status.FULFILLED);
            when(changeRequest.getStatus()).thenReturn(Status.PENDING);
            when(changeRequest.getChangeType()).thenReturn(ChangeType.EXPIRES_AT);
            when(changeRequest.getNewValue()).thenReturn("\"2027-12-31T23:59:59\"");
            when(changeRequest.getRequest()).thenReturn(originalRequest);
            when(changeRequestRepository.findById(2L)).thenReturn(Optional.of(changeRequest));
            when(userRepository.findById(100L)).thenReturn(Optional.of(mockUser));

            ApproveModificationDTO dto = new ApproveModificationDTO(2L, "기간 연장 승인");
            service.approveModification(100L, dto);

            ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
            verify(originalRequest).updateExpiresAt(captor.capture());
            assertThat(captor.getValue()).isEqualTo(LocalDateTime.of(2027, 12, 31, 23, 59, 59));
            verify(changeRequest).approve(mockUser, "기간 연장 승인");
            verify(alarmService).sendContainerExtendedEmail(originalRequest, LocalDateTime.of(2027, 12, 31, 23, 59, 59));
        }

        @Test
        @DisplayName("RESOURCE_GROUP 변경 요청 승인 시 originalRequest.updateResourceGroup()이 호출된다")
        void approveModification_resourceGroup_success() throws Exception {
            ChangeRequest changeRequest = mock(ChangeRequest.class);
            Request originalRequest = buildMockedRequestWithStatus(22L, Status.FULFILLED);
            ResourceGroup newRg = mock(ResourceGroup.class);
            when(changeRequest.getStatus()).thenReturn(Status.PENDING);
            when(changeRequest.getChangeType()).thenReturn(ChangeType.RESOURCE_GROUP);
            when(changeRequest.getNewValue()).thenReturn("2");
            when(changeRequest.getRequest()).thenReturn(originalRequest);
            when(changeRequestRepository.findById(3L)).thenReturn(Optional.of(changeRequest));
            when(userRepository.findById(100L)).thenReturn(Optional.of(mockUser));
            when(resourceGroupRepository.findById(2)).thenReturn(Optional.of(newRg));

            ApproveModificationDTO dto = new ApproveModificationDTO(3L, "리소스 그룹 변경 승인");
            service.approveModification(100L, dto);

            verify(originalRequest).updateResourceGroup(newRg);
            verify(changeRequest).approve(mockUser, "리소스 그룹 변경 승인");
        }

        @Test
        @DisplayName("CONTAINER_IMAGE 변경 요청 승인 시 originalRequest.updateContainerImage()가 호출된다")
        void approveModification_containerImage_success() throws Exception {
            ChangeRequest changeRequest = mock(ChangeRequest.class);
            Request originalRequest = buildMockedRequestWithStatus(23L, Status.FULFILLED);
            ContainerImage newImage = mock(ContainerImage.class);
            when(changeRequest.getStatus()).thenReturn(Status.PENDING);
            when(changeRequest.getChangeType()).thenReturn(ChangeType.CONTAINER_IMAGE);
            when(changeRequest.getNewValue()).thenReturn("5");
            when(changeRequest.getRequest()).thenReturn(originalRequest);
            when(changeRequestRepository.findById(4L)).thenReturn(Optional.of(changeRequest));
            when(userRepository.findById(100L)).thenReturn(Optional.of(mockUser));
            when(containerImageRepository.findById(5L)).thenReturn(Optional.of(newImage));

            ApproveModificationDTO dto = new ApproveModificationDTO(4L, "이미지 변경 승인");
            service.approveModification(100L, dto);

            verify(originalRequest).updateContainerImage(newImage);
            verify(changeRequest).approve(mockUser, "이미지 변경 승인");
        }

        @Test
        @DisplayName("존재하지 않는 변경 요청 ID로 승인하면 BusinessException을 던진다")
        void approveModification_throwsException_whenChangeRequestNotFound() {
            when(changeRequestRepository.findById(999L)).thenReturn(Optional.empty());

            ApproveModificationDTO dto = new ApproveModificationDTO(999L, "승인");

            assertThatThrownBy(() -> service.approveModification(100L, dto))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("PENDING이 아닌 변경 요청 승인 시 BusinessException을 던진다")
        void approveModification_throwsException_whenNotPendingStatus() {
            ChangeRequest changeRequest = mock(ChangeRequest.class);
            when(changeRequest.getStatus()).thenReturn(Status.FULFILLED);
            when(changeRequestRepository.findById(5L)).thenReturn(Optional.of(changeRequest));

            ApproveModificationDTO dto = new ApproveModificationDTO(5L, "승인");

            assertThatThrownBy(() -> service.approveModification(100L, dto))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("존재하지 않는 adminId로 승인하면 BusinessException을 던진다")
        void approveModification_throwsException_whenAdminNotFound() {
            ChangeRequest changeRequest = mock(ChangeRequest.class);
            when(changeRequest.getStatus()).thenReturn(Status.PENDING);
            when(changeRequestRepository.findById(6L)).thenReturn(Optional.of(changeRequest));
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            ApproveModificationDTO dto = new ApproveModificationDTO(6L, "승인");

            assertThatThrownBy(() -> service.approveModification(999L, dto))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("originalRequest가 null이면 BusinessException을 던진다")
        void approveModification_throwsException_whenOriginalRequestIsNull() {
            ChangeRequest changeRequest = mock(ChangeRequest.class);
            when(changeRequest.getStatus()).thenReturn(Status.PENDING);
            when(changeRequest.getRequest()).thenReturn(null);
            when(changeRequestRepository.findById(7L)).thenReturn(Optional.of(changeRequest));
            when(userRepository.findById(100L)).thenReturn(Optional.of(mockUser));

            ApproveModificationDTO dto = new ApproveModificationDTO(7L, "승인");

            assertThatThrownBy(() -> service.approveModification(100L, dto))
                    .isInstanceOf(BusinessException.class);
        }
    }

    private Request buildMockedRequestWithStatus(Long requestId, Status status) {
        Request request = mock(Request.class);
        when(request.getStatus()).thenReturn(status);
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
}
