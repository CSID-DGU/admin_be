package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.domain.alarm.service.AlarmService;
import DGU_AI_LAB.admin_be.domain.containerImage.entity.ContainerImage;
import DGU_AI_LAB.admin_be.domain.containerImage.repository.ContainerImageRepository;
import DGU_AI_LAB.admin_be.domain.groups.entity.Group;
import DGU_AI_LAB.admin_be.domain.groups.repository.GroupRepository;
import DGU_AI_LAB.admin_be.domain.groups.service.GroupService;
import DGU_AI_LAB.admin_be.domain.pod.entity.PodExternalPort;
import DGU_AI_LAB.admin_be.domain.pod.repository.PodExternalPortRepository;
import DGU_AI_LAB.admin_be.domain.portRequests.service.PortRequestService;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.ApproveModificationDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.ApproveRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.RejectModificationDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.RejectRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.UserCreationRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.CreatePodResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.SaveRequestResponseDTO;
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
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
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
    @Mock private GroupService groupService;
    @Mock private PodExternalPortRepository podExternalPortRepository;
    @Mock private PodService podService;
    @Mock private UbuntuAccountService ubuntuAccountService;
    @Mock private PortRequestService portRequestService;
    @Mock private WebClient mockWebClient;
    @Mock private PlatformTransactionManager transactionManager;
    @Mock private TransactionStatus transactionStatus;
    @Mock private ThreadPoolTaskExecutor approvalExecutor;

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
                groupRepository, groupService, podExternalPortRepository, podService, ubuntuAccountService, portRequestService, new ObjectMapper(),
                mockWebClient, transactionManager, approvalExecutor
        );
        // 공유 엔티티 기본 설정
        when(mockUser.getName()).thenReturn("테스트유저");
        when(mockUser.getUserId()).thenReturn(100L);
        // 기본값: 아직 리눅스 계정이 없는 사용자 → 승인 시 계정 생성 API를 호출한다.
        when(mockUser.hasUbuntuAccount()).thenReturn(false);
        // UID/GID 배정은 User 행을 잠그고 수행한다 (같은 사용자의 동시 승인 직렬화).
        when(userRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(mockUser));
        when(mockImage.getImageId()).thenReturn(1L);
        when(mockRg.getRsgroupId()).thenReturn(1);

        // approvalExecutor.execute(...)는 실제 스레드풀 없이 제출된 작업을 그 자리에서
        // 동기 실행한다 — 유닛 테스트에는 별도 스레드가 필요 없고, 이렇게 해야 기존
        // 테스트들이 approveRequest() 호출 직후 바로 부수효과를 검증할 수 있다. 동시
        // 처리 한도 초과 테스트만 이 stub을 덮어써서 TaskRejectedException을 던지게 한다.
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return null;
        }).when(approvalExecutor).execute(any());
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
        // 1단계(승인 시작)에서는 PENDING을 확인하고, 3단계(외부 호출 완료 후 DB 반영)에서는
        // markAsProcessing()으로 바뀐 PROCESSING을 재확인한다. mock이라 실제로 상태가
        // 바뀌진 않으므로, 호출 순서에 맞춰 반환값을 순차 지정한다.
        when(request.getStatus()).thenReturn(Status.PENDING, Status.PROCESSING);
        when(request.getUbuntuUsername()).thenReturn("testuser");
        when(request.getUbuntuPassword()).thenReturn("encoded_pw");
        when(request.getUbuntuPasswordBase64()).thenReturn("cGxhaW5fdGV4dF9wdw==");
        when(request.getRequestGroups()).thenReturn(new LinkedHashSet<>());
        when(request.getUser()).thenReturn(mockUser);
        when(request.getResourceGroup()).thenReturn(mockRg);
        when(request.getContainerImage()).thenReturn(mockImage);
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(requestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));
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

        ApproveRequestDTO dto = new ApproveRequestDTO(requestId, 1L, 1, "승인합니다");

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
        assertThat(userCreationRequest.supplementaryGroups()).isEmpty();
    }

    @Test
    @DisplayName("승인 시 Request에 딸린 그룹이 supplementary_groups로 계정 생성 API에 전달된다")
    void approveRequest_sendsRequestGroupsAsSupplementaryGroups() {
        // Given
        Long requestId = 17L;
        Request request = mock(Request.class);
        when(request.getStatus()).thenReturn(Status.PENDING, Status.PROCESSING);
        when(request.getUbuntuUsername()).thenReturn("testuser");
        when(request.getUbuntuPasswordBase64()).thenReturn("cGxhaW5fdGV4dF9wdw==");

        Group ascp = mock(Group.class);
        when(ascp.getGroupName()).thenReturn("ASCP");
        when(ascp.getUbuntuGid()).thenReturn(20004L);
        DGU_AI_LAB.admin_be.domain.requests.entity.RequestGroup requestGroup =
                mock(DGU_AI_LAB.admin_be.domain.requests.entity.RequestGroup.class);
        when(requestGroup.getGroup()).thenReturn(ascp);
        when(request.getRequestGroups()).thenReturn(new LinkedHashSet<>(List.of(requestGroup)));

        when(request.getUser()).thenReturn(mockUser);
        when(request.getResourceGroup()).thenReturn(mockRg);
        when(request.getContainerImage()).thenReturn(mockImage);
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(requestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));
        stubWebClientPut();

        CreatePodResponseDTO podResponse = new CreatePodResponseDTO("running", "farm1", "pod-testuser-xxxx", List.of());
        when(podService.createPod("testuser")).thenReturn(podResponse);
        when(containerImageRepository.findById(1L)).thenReturn(Optional.of(mockImage));
        when(resourceGroupRepository.findById(1)).thenReturn(Optional.of(mockRg));

        ApproveRequestDTO dto = new ApproveRequestDTO(requestId, 1L, 1, "승인합니다");

        // When
        service.approveRequest(dto);

        // Then
        ArgumentCaptor<UserCreationRequestDTO> userCreationCaptor = ArgumentCaptor.forClass(UserCreationRequestDTO.class);
        verify(putBodySpec).bodyValue(userCreationCaptor.capture());
        List<UserCreationRequestDTO.SupplementaryGroup> supplementaryGroups = userCreationCaptor.getValue().supplementaryGroups();
        assertThat(supplementaryGroups).hasSize(1);
        assertThat(supplementaryGroups.get(0).name()).isEqualTo("ASCP");
        assertThat(supplementaryGroups.get(0).gid()).isEqualTo(20004L);
        // 계정 생성 API 호출에도 그룹이 실렸지만, 별도로 groupService를 통한 멱등 그룹
        // 추가도 반드시 호출돼야 한다 — 재사용 계정 경로는 계정 생성 API 자체를 건너뛰므로
        // 이 별도 호출 없이는 재사용 승인에서 그룹이 전혀 반영되지 않는다.
        verify(groupService).addUserToGroups("testuser", List.of("ASCP"));
    }

    @Test
    @DisplayName("계정을 재사용하는 승인도 이번 신청의 그룹을 계정에 추가한다 — 안 그러면 최초 승인 때 그룹에 영구히 고정된다")
    void approveRequest_reusedAccount_stillSyncsThisRequestsGroups() {
        Long requestId = 66L;
        Request request = mock(Request.class);
        when(request.getStatus()).thenReturn(Status.PENDING, Status.PROCESSING);
        when(request.getUbuntuUsername()).thenReturn("testuser");
        when(request.getUbuntuPasswordBase64()).thenReturn("cGxhaW5fdGV4dF9wdw==");

        Group vision = mock(Group.class);
        when(vision.getGroupName()).thenReturn("VISION-LAB");
        when(vision.getUbuntuGid()).thenReturn(20005L);
        DGU_AI_LAB.admin_be.domain.requests.entity.RequestGroup requestGroup =
                mock(DGU_AI_LAB.admin_be.domain.requests.entity.RequestGroup.class);
        when(requestGroup.getGroup()).thenReturn(vision);
        when(request.getRequestGroups()).thenReturn(new LinkedHashSet<>(List.of(requestGroup)));

        when(request.getUser()).thenReturn(mockUser);
        when(request.getResourceGroup()).thenReturn(mockRg);
        when(request.getContainerImage()).thenReturn(mockImage);
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(requestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));
        // 이미 리눅스 계정(UID/GID)을 보유한 사용자로 바꾼다 — 두 번째 이후의 승인 상황.
        when(mockUser.hasUbuntuAccount()).thenReturn(true);
        when(mockUser.getUbuntuUid()).thenReturn(20001L);
        when(mockUser.getUbuntuGid()).thenReturn(20001L);

        when(podService.createPod("testuser")).thenReturn(
                new CreatePodResponseDTO("running", "farm1", "pod-testuser-reuse-grp", List.of()));
        when(containerImageRepository.findById(1L)).thenReturn(Optional.of(mockImage));
        when(resourceGroupRepository.findById(1)).thenReturn(Optional.of(mockRg));

        service.approveRequest(new ApproveRequestDTO(requestId, 1L, 1, "승인"));

        // 계정 생성 API는 안 불렸지만(재사용 경로),
        verify(mockWebClient, never()).put();
        // 이번 신청의 그룹은 별도로 반영돼야 한다.
        verify(groupService).addUserToGroups("testuser", List.of("VISION-LAB"));
    }

    @Test
    @DisplayName("그룹 추가가 실패하면 상태를 PENDING으로 되돌린다")
    void approveRequest_groupSyncFails_revertsToPending() {
        Long requestId = 67L;
        Request request = buildMockedRequest(requestId);
        stubWebClientPut();

        Group ascp = mock(Group.class);
        when(ascp.getGroupName()).thenReturn("ASCP");
        when(ascp.getUbuntuGid()).thenReturn(20004L);
        DGU_AI_LAB.admin_be.domain.requests.entity.RequestGroup requestGroup =
                mock(DGU_AI_LAB.admin_be.domain.requests.entity.RequestGroup.class);
        when(requestGroup.getGroup()).thenReturn(ascp);
        when(request.getRequestGroups()).thenReturn(new LinkedHashSet<>(List.of(requestGroup)));

        doThrow(new BusinessException(ErrorCode.GROUP_CREATION_FAILED))
                .when(groupService).addUserToGroups(eq("testuser"), anyList());

        service.approveRequest(new ApproveRequestDTO(requestId, 1L, 1, "승인"));

        verify(podService, never()).createPod(anyString());
        verify(request).revertToPending();
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

        ApproveRequestDTO dto = new ApproveRequestDTO(requestId, 1L, 1, "승인합니다");

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

        ApproveRequestDTO dto = new ApproveRequestDTO(requestId, 1L, 1, null);

        // When
        service.approveRequest(dto);

        // Then - PodService.createPod()가 username으로 정확히 1회 호출
        verify(podService, times(1)).createPod("testuser");
    }

    @Test
    @DisplayName("배정 안내 메일 발송이 실패해도 예외가 전파되지 않고 정상 응답 DTO가 반환된다")
    void approveRequest_containerCreatedEmailFails_doesNotPropagateAndStillReturnsDto() {
        // Given
        Long requestId = 4L;
        buildMockedRequest(requestId);
        stubWebClientPut();

        CreatePodResponseDTO podResponse = new CreatePodResponseDTO(
                "running", "farm1", "pod-testuser-mail-fail", List.of()
        );
        when(podService.createPod("testuser")).thenReturn(podResponse);
        when(containerImageRepository.findById(1L)).thenReturn(Optional.of(mockImage));
        when(resourceGroupRepository.findById(1)).thenReturn(Optional.of(mockRg));
        doThrow(new RuntimeException("SMTP 연결 실패"))
                .when(alarmService).sendContainerCreatedEmail(any(), anyString(), anyString());

        ApproveRequestDTO dto = new ApproveRequestDTO(requestId, 1L, 1, "승인");

        // When / Then - 이메일 실패해도 예외 없이 정상 반환 (Pod·계정은 이미 생성된 상태이므로 롤백 안 함)
        assertThat(service.approveRequest(dto)).isNotNull();
        verify(ubuntuAccountService, never()).deleteUbuntuAccount(any());
    }

    // ── C-3: Pod 생성 null 응답 / NPE 보상 트랜잭션 테스트 ─────────────────

    @Nested
    @DisplayName("웹 계정당 우분투 계정 하나")
    class AccountScopedToWebAccount {

        /** 이미 리눅스 계정(UID/GID)을 보유한 사용자로 바꾼다 — 두 번째 이후의 승인 상황. */
        private void givenUserAlreadyHasUbuntuAccount() {
            when(mockUser.hasUbuntuAccount()).thenReturn(true);
            when(mockUser.getUbuntuUid()).thenReturn(20001L);
            when(mockUser.getUbuntuGid()).thenReturn(20001L);
        }

        @Test
        @DisplayName("이미 계정을 가진 사용자의 두 번째 승인은 계정 생성 API를 호출하지 않고 기존 UID/GID를 그대로 쓴다")
        void secondApproval_skipsAccountCreationAndReusesUidGid() {
            Long requestId = 60L;
            Request request = buildMockedRequest(requestId);
            givenUserAlreadyHasUbuntuAccount();

            when(podService.createPod("testuser")).thenReturn(
                    new CreatePodResponseDTO("running", "farm1", "pod-testuser-2nd", List.of()));
            when(containerImageRepository.findById(1L)).thenReturn(Optional.of(mockImage));
            when(resourceGroupRepository.findById(1)).thenReturn(Optional.of(mockRg));

            service.approveRequest(new ApproveRequestDTO(requestId, 1L, 1, "승인"));

            // 계정 생성 PUT은 한 번도 나가지 않아야 한다. 다시 만들면 UID가 바뀌어
            // 기존 홈 디렉터리(/home/testuser)의 소유권이 어긋난다.
            verify(mockWebClient, never()).put();
            // 같은 유저네임/UID/GID로 Pod를 만들었으므로 홈 디렉터리가 그대로 이어진다.
            verify(podService).createPod("testuser");
            verify(request).assignUbuntuIds(20001L, 20001L);
            verify(mockUser).assignUbuntuAccount(20001L, 20001L);
        }

        @Test
        @DisplayName("UID/GID 배정은 User 행을 잠그고 수행한다 — 같은 사용자의 승인 두 건이 각자 계정을 만들지 못하게 직렬화한다")
        void assignsUidUnderUserRowLock() {
            Long requestId = 61L;
            buildMockedRequest(requestId);
            stubWebClientPut();

            when(podService.createPod("testuser")).thenReturn(
                    new CreatePodResponseDTO("running", "farm1", "pod-testuser-lock", List.of()));
            when(containerImageRepository.findById(1L)).thenReturn(Optional.of(mockImage));
            when(resourceGroupRepository.findById(1)).thenReturn(Optional.of(mockRg));

            service.approveRequest(new ApproveRequestDTO(requestId, 1L, 1, "승인"));

            // 계정 생성 직후(Pod 생성 전) 한 번, 최종 DB 반영 트랜잭션에서 한 번 — 총 두 번
            // User 행을 잠그고 배정한다. 두 번째는 같은 값이라 항상 멱등하게 통과한다.
            verify(userRepository, times(2)).findByIdForUpdate(100L);
            verify(userRepository, never()).findById(100L);
            verify(mockUser, times(2)).assignUbuntuAccount(2001L, 2001L);
        }

        @Test
        @DisplayName("계정 생성 직후 배정에서 다른 UID가 이미 배정돼 있으면 Pod를 만들기 전에 중단한다")
        void conflictingAssignmentRightAfterAccountCreation_stopsBeforeCreatingPod() {
            Long requestId = 62L;
            Request request = buildMockedRequest(requestId);
            stubWebClientPut();
            // 계정 생성 API 호출 직후 벌어지는 배정 시도 시점에 다른 승인이 먼저 다른 UID를
            // 배정해 놓은 상황 — Pod 생성 전에 잡히므로 Pod는 아예 만들어지지 않는다.
            doThrow(new BusinessException(ErrorCode.UBUNTU_ACCOUNT_ALREADY_ASSIGNED))
                    .when(mockUser).assignUbuntuAccount(2001L, 2001L);

            service.approveRequest(new ApproveRequestDTO(requestId, 1L, 1, "승인"));

            // Pod를 만든 적이 없으므로 지울 것도 없다.
            verify(podService, never()).createPod(anyString());
            verify(podService, never()).deletePod(anyString());
            // 이 시점엔 아직 어느 farm 노드에도 배포된 게 없어 node를 모른다 — node_name 없이
            // 삭제를 호출하면 무관한 동명 레거시 계정까지 지울 수 있으므로 삭제 자체를 보류한다
            // (방금 만든 계정은 남고, 재승인 시 hasUbuntuAccount()로 재사용된다).
            verify(ubuntuAccountService, never()).deleteUbuntuAccount(anyString(), any());
            verify(request).revertToPending();
        }

        @Test
        @DisplayName("Pod 생성 후 DB 반영 단계에서 배정 충돌이 나면 계정/Pod를 되돌린다")
        void conflictingAssignmentAtFinalCommit_compensatesAndRevertsRequest() {
            Long requestId = 65L;
            Request request = buildMockedRequest(requestId);
            stubWebClientPut();

            when(podService.createPod("testuser")).thenReturn(
                    new CreatePodResponseDTO("running", "farm1", "pod-testuser-race2", List.of()));
            when(containerImageRepository.findById(1L)).thenReturn(Optional.of(mockImage));
            when(resourceGroupRepository.findById(1)).thenReturn(Optional.of(mockRg));
            // 계정 생성 직후 배정은 통과시키고(첫 번째 호출), Pod 생성 후 최종 DB 반영 단계의
            // 배정(두 번째 호출)에서만 다른 승인이 먼저 다른 UID를 배정해 놓은 상황을 재현한다.
            doNothing()
                    .doThrow(new BusinessException(ErrorCode.UBUNTU_ACCOUNT_ALREADY_ASSIGNED))
                    .when(mockUser).assignUbuntuAccount(2001L, 2001L);

            service.approveRequest(new ApproveRequestDTO(requestId, 1L, 1, "승인"));

            verify(podService).deletePod("pod-testuser-race2");
            verify(ubuntuAccountService).deleteUbuntuAccount("testuser", "farm1");
            verify(request).revertToPending();
        }

        @Test
        @DisplayName("재사용한 계정은 Pod 생성이 실패해도 지우지 않는다 — 지우면 이 사용자의 홈 디렉터리까지 사라진다")
        void podFailureAfterReusingAccount_doesNotDeleteTheSharedAccount() {
            Long requestId = 63L;
            Request request = buildMockedRequest(requestId);
            givenUserAlreadyHasUbuntuAccount();

            when(podService.createPod("testuser"))
                    .thenThrow(new BusinessException(ErrorCode.POD_CREATION_FAILED));

            service.approveRequest(new ApproveRequestDTO(requestId, 1L, 1, "승인"));

            verify(ubuntuAccountService, never()).deleteUbuntuAccount(anyString(), any());
            verify(request).revertToPending();
        }

        @Test
        @DisplayName("재사용한 계정은 DB 반영이 실패해도 Pod만 지우고 계정은 남긴다")
        void dbFailureAfterReusingAccount_deletesPodButKeepsTheAccount() {
            Long requestId = 64L;
            Request request = buildMockedRequest(requestId);
            givenUserAlreadyHasUbuntuAccount();

            when(podService.createPod("testuser")).thenReturn(
                    new CreatePodResponseDTO("running", "farm1", "pod-testuser-dbfail", List.of()));
            when(containerImageRepository.findById(1L)).thenReturn(Optional.empty());

            service.approveRequest(new ApproveRequestDTO(requestId, 1L, 1, "승인"));

            verify(podService).deletePod("pod-testuser-dbfail");
            verify(ubuntuAccountService, never()).deleteUbuntuAccount(anyString(), any());
            verify(request).revertToPending();
        }
    }

    @Nested
    @DisplayName("C-3: Pod 생성 실패 시 보상 트랜잭션")
    class PodCreationFailureCompensation {

        @Test
        @DisplayName("createPod()가 BusinessException을 던지면 Ubuntu 계정 삭제 및 상태 복구가 호출된다")
        void approveRequest_podThrowsBusinessException_compensatesWithAccountDeletion() {
            Long requestId = 10L;
            Request request = buildMockedRequest(requestId);
            stubWebClientPut();

            when(podService.createPod("testuser"))
                    .thenThrow(new BusinessException(ErrorCode.POD_CREATION_FAILED));

            ApproveRequestDTO dto = new ApproveRequestDTO(requestId, 1L, 1, "승인");

            // 후처리는 비동기(테스트에서는 approvalExecutor mock이 즉시 동기 실행)라, 실패해도
            // 이미 응답을 반환한 approveRequest() 자체는 예외를 던지지 않는다 — 보상 트랜잭션이
            // 내부적으로 처리됐는지를 검증한다.
            SaveRequestResponseDTO response = service.approveRequest(dto);
            assertThat(response).isNotNull();

            // createPod가 던진 게 PodCreationFailedException이 아닌 평범한 BusinessException이라
            // 실패 노드를 못 얻는다 — node_name 없이 삭제를 호출하면 config-server가 모든 farm
            // 노드를 훑어 무관한 동명 레거시 계정까지 지울 수 있으므로, 이제는 삭제를 보류하고
            // 알림만 보낸다(a3a8e21이 AdminUserService에 적용한 것과 동일한 가드).
            verify(ubuntuAccountService, never()).deleteUbuntuAccount(anyString(), any());
            // 1단계(승인 시작)와 revertToPendingIfStillProcessing 모두 findByIdForUpdate(행
            // 잠금)로 Request를 조회한다 — 락 없는 findById 재조회는 동시 거절 결과를 덮어쓸
            // 수 있어 더 이상 쓰지 않는다.
            verify(requestRepository, times(2)).findByIdForUpdate(requestId);
            verify(requestRepository, never()).findById(requestId);
            verify(request).revertToPending();
        }

        @Test
        @DisplayName("createPod()가 실패하면 계정 삭제 대신 보류하고, 상태는 복구된다")
        void approveRequest_podReturnsNull_throwsAndCompensates() {
            Long requestId = 11L;
            buildMockedRequest(requestId);
            stubWebClientPut();

            when(podService.createPod("testuser"))
                    .thenThrow(new BusinessException(ErrorCode.POD_CREATION_FAILED));

            ApproveRequestDTO dto = new ApproveRequestDTO(requestId, 1L, 1, "승인");

            assertThat(service.approveRequest(dto)).isNotNull();

            verify(ubuntuAccountService, never()).deleteUbuntuAccount(anyString(), any());
            verify(containerImageRepository, never()).findById(any());
        }

        @Test
        @DisplayName("동시 처리 한도(3) 초과 시 승인 후처리 제출 자체가 즉시 실패하고 보상 트랜잭션이 실행된다")
        void approveRequest_concurrencyLimitExceeded_failsFastWithoutCallingCreatePod() {
            Long requestId = 20L;
            buildMockedRequest(requestId);
            stubWebClientPut();

            // executor 큐가 꽉 차서(동시 3건 초과) 거부되는 상황을 재현 — 제출 자체가
            // TaskRejectedException을 던지므로 processApproval은 아예 실행되지 않는다.
            doThrow(new TaskRejectedException("executor saturated"))
                    .when(approvalExecutor).execute(any());

            ApproveRequestDTO dto = new ApproveRequestDTO(requestId, 1L, 1, "승인");

            assertThatThrownBy(() -> service.approveRequest(dto))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.POD_CREATION_CONCURRENCY_LIMIT);

            // 제출 자체가 거부됐으므로 계정 생성 API 호출도, Pod 생성도 전혀 일어나지 않는다 —
            // 오늘의 3-한도가 Pod 생성뿐 아니라 계정 생성까지 함께 가드하도록 넓어진 부분이다.
            verify(putBodySpec, never()).bodyValue(any());
            verify(podService, never()).createPod(any());
            verify(ubuntuAccountService, never()).deleteUbuntuAccount(any(), any());
        }

        @Test
        @DisplayName("createPod() 실패 시 실패 노드를 모르면 계정 삭제를 시도조차 하지 않고 보류 알림만 보낸다")
        void approveRequest_podFailsWithUnknownNode_holdsDeletionAndAlertsOnce() {
            Long requestId = 12L;
            buildMockedRequest(requestId);
            stubWebClientPut();

            when(podService.createPod("testuser"))
                    .thenThrow(new BusinessException(ErrorCode.POD_CREATION_FAILED));

            ApproveRequestDTO dto = new ApproveRequestDTO(requestId, 1L, 1, "승인");

            assertThat(service.approveRequest(dto)).isNotNull();

            // node_name 없이 삭제를 호출하면 config-server가 모든 farm 노드를 훑어 무관한 동명
            // 레거시 계정까지 지울 수 있으므로, 삭제 자체를 시도하지 않는다.
            verify(ubuntuAccountService, never()).deleteUbuntuAccount(anyString(), any());
            // 원래 실패(Pod 생성 실패) 알림 + 삭제 보류 알림, 총 두 번 Slack으로 알린다 —
            // 관리자가 실제로 보는 farm/lab 채널로 보낸다(serverName은 이 테스트에서 스텁
            // 안 해 null).
            verify(alarmService, times(2)).sendAdminSlackNotification(isNull(), contains("testuser"));
        }

        @Test
        @DisplayName("createPod() 실패 노드를 알면 보상 계정 삭제를 시도하고, 그 삭제 자체가 실패해도 처리는 종료되며 두 알림 모두 보낸다")
        void approveRequest_podFailsWithKnownNode_compensationDeleteAlsoFails_alertsBoth() {
            Long requestId = 16L;
            buildMockedRequest(requestId);
            stubWebClientPut();

            when(podService.createPod("testuser"))
                    .thenThrow(new PodCreationFailedException("pod 생성 실패", ErrorCode.POD_CREATION_FAILED, "farm1"));
            doThrow(new RuntimeException("계정 삭제 실패"))
                    .when(ubuntuAccountService).deleteUbuntuAccount("testuser", "farm1");

            ApproveRequestDTO dto = new ApproveRequestDTO(requestId, 1L, 1, "승인");

            assertThat(service.approveRequest(dto)).isNotNull();

            verify(ubuntuAccountService).deleteUbuntuAccount("testuser", "farm1");
            // 보상 트랜잭션 자체의 실패는 로그만 남으면 아무도 모른다 — 원래 실패(Pod 생성 실패)
            // 알림에 더해 삭제 실패 알림까지 총 두 번 Slack으로 보낸다.
            verify(alarmService, times(2)).sendAdminSlackNotification(isNull(), contains("testuser"));
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

            ApproveRequestDTO dto = new ApproveRequestDTO(requestId, 1L, 1, "승인");

            service.approveRequest(dto);

            verify(ubuntuAccountService, never()).deleteUbuntuAccount(any());
            verify(podService, never()).deletePod(any());
        }

        @Test
        @DisplayName("외부 호출 도중 다른 관리자가 거절해 상태가 바뀌면, 방금 만든 계정/Pod를 정리하고 승인을 덮어쓰지 않는다")
        void approveRequest_statusChangedDuringExternalCalls_compensatesInsteadOfOverwriting() {
            Long requestId = 15L;
            Request request = mock(Request.class);
            // 1단계에서는 PENDING(승인 시작 허용), 3단계 재확인 시점엔 DENIED(그 사이 다른
            // 관리자가 거절함)를 반환하도록 순차 스텁한다 — mock이라 markAsProcessing()이
            // 실제 상태를 바꾸지 않으므로, 여기서 "그 사이 거절됨" 상황을 직접 흉내낸다.
            when(request.getStatus()).thenReturn(Status.PENDING, Status.DENIED);
            when(request.getUbuntuUsername()).thenReturn("testuser");
            when(request.getUbuntuPasswordBase64()).thenReturn("cGxhaW5fdGV4dF9wdw==");
            when(request.getUser()).thenReturn(mockUser);
            when(request.getResourceGroup()).thenReturn(mockRg);
            when(request.getContainerImage()).thenReturn(mockImage);
            when(requestRepository.findById(requestId)).thenReturn(Optional.of(request));
            when(requestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));
            stubWebClientPut();

            CreatePodResponseDTO podResponse = new CreatePodResponseDTO(
                    "running", "farm1", "pod-testuser-race", List.of()
            );
            when(podService.createPod("testuser")).thenReturn(podResponse);

            ApproveRequestDTO dto = new ApproveRequestDTO(requestId, 1L, 1, "승인");

            // 후처리는 비동기라 그 안에서 발생하는 INVALID_REQUEST_STATUS는 approveRequest()
            // 밖으로 전파되지 않는다 — 보상 트랜잭션(계정/Pod 정리)이 내부적으로 수행됐는지 검증한다.
            assertThat(service.approveRequest(dto)).isNotNull();

            // 이미 만든 계정/Pod는 정리하되, 거절된 요청의 상태를 승인으로 덮어쓰지 않는다
            verify(podService).deletePod("pod-testuser-race");
            // 이 경로는 Pod 생성까지는 성공했으므로 finalPodResponse.node()("farm1")로 좁혀서 정리한다.
            verify(ubuntuAccountService).deleteUbuntuAccount("testuser", "farm1");
            verify(request, never()).approve(any(), any(), any());
            // 이미 DENIED로 정상 종료된 요청이므로 PENDING으로 되돌리지 않는다
            verify(request, never()).revertToPending();
        }

        @Test
        @DisplayName("DB 반영 단계가 상태 변경이 아닌 다른 이유로 실패하면 PENDING으로 되돌려 재승인 가능하게 한다")
        void approveRequest_dbSaveFailsWhileStillProcessing_revertsToPending() {
            Long requestId = 16L;
            Request request = mock(Request.class);
            // 1단계 PENDING(승인 시작), 3단계 재확인 시점엔 여전히 PROCESSING(동시 거절 없음)
            // — 그런데도 이미지 조회 실패 같은 다른 이유로 DB 반영이 실패하는 상황을 재현한다.
            when(request.getStatus()).thenReturn(Status.PENDING, Status.PROCESSING, Status.PROCESSING);
            when(request.getUbuntuUsername()).thenReturn("testuser");
            when(request.getUbuntuPasswordBase64()).thenReturn("cGxhaW5fdGV4dF9wdw==");
            when(request.getUser()).thenReturn(mockUser);
            when(request.getResourceGroup()).thenReturn(mockRg);
            when(request.getContainerImage()).thenReturn(mockImage);
            when(requestRepository.findById(requestId)).thenReturn(Optional.of(request));
            when(requestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));
            stubWebClientPut();

            CreatePodResponseDTO podResponse = new CreatePodResponseDTO(
                    "running", "farm1", "pod-testuser-orphan", List.of()
            );
            when(podService.createPod("testuser")).thenReturn(podResponse);
            when(containerImageRepository.findById(1L)).thenReturn(Optional.empty());

            ApproveRequestDTO dto = new ApproveRequestDTO(requestId, 1L, 1, "승인");

            assertThat(service.approveRequest(dto)).isNotNull();

            // 인프라는 정리하고, 여전히 PROCESSING이었던 요청은 PENDING으로 되돌려
            // 재승인/재거절이 막힌 채 영구히 갇히지 않게 한다
            verify(podService).deletePod("pod-testuser-orphan");
            verify(ubuntuAccountService).deleteUbuntuAccount("testuser", "farm1");
            verify(request).revertToPending();
        }

        @Test
        @DisplayName("PENDING 상태가 아닌 요청 승인 시 BusinessException 발생")
        void approveRequest_nonPendingStatus_throwsBusinessException() {
            Long requestId = 14L;
            Request request = mock(Request.class);
            when(request.getStatus()).thenReturn(Status.FULFILLED);
            when(requestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));

            ApproveRequestDTO dto = new ApproveRequestDTO(requestId, 1L, 1, "승인");

            assertThatThrownBy(() -> service.approveRequest(dto))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_REQUEST_STATUS);

            verify(podService, never()).createPod(any());
        }

        @Test
        @DisplayName("존재하지 않는 requestId 승인 시 BusinessException 발생")
        void approveRequest_requestNotFound_throwsBusinessException() {
            when(requestRepository.findByIdForUpdate(999L)).thenReturn(Optional.empty());

            ApproveRequestDTO dto = new ApproveRequestDTO(999L, 1L, 1, "승인");

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

        @Test
        @DisplayName("거절 성공 시 alarmService.sendRequestRejectedEmail이 호출된다")
        void rejectRequest_sendsRejectionEmail_onSuccess() {
            Request request = buildMockedRequestWithStatus(35L, Status.PENDING);
            RejectRequestDTO dto = new RejectRequestDTO(35L, "신청서 양식 미흡");

            service.rejectRequest(dto);

            verify(alarmService).sendRequestRejectedEmail(request, "신청서 양식 미흡");
        }

        @Test
        @DisplayName("거절 사유가 이메일 발송 메서드에 그대로 전달된다")
        void rejectRequest_passesAdminCommentToEmail() {
            Request request = buildMockedRequestWithStatus(36L, Status.PENDING);
            String comment = "서버 정원 초과로 인한 거절";
            RejectRequestDTO dto = new RejectRequestDTO(36L, comment);

            service.rejectRequest(dto);

            verify(alarmService).sendRequestRejectedEmail(request, comment);
        }

        @Test
        @DisplayName("유효하지 않은 상태로 거절 시 이메일이 발송되지 않는다")
        void rejectRequest_doesNotSendEmail_whenStatusIsInvalid() {
            buildMockedRequestWithStatus(37L, Status.DENIED);
            RejectRequestDTO dto = new RejectRequestDTO(37L, "사유");

            assertThatThrownBy(() -> service.rejectRequest(dto))
                    .isInstanceOf(BusinessException.class);

            verify(alarmService, never()).sendRequestRejectedEmail(any(), anyString());
        }

        @Test
        @DisplayName("이메일 발송 실패 시 예외가 전파되지 않고 reject()는 이미 호출된 상태다")
        void rejectRequest_emailFailure_doesNotPropagateException() {
            Request request = buildMockedRequestWithStatus(38L, Status.PENDING);
            doThrow(new RuntimeException("SMTP 연결 실패"))
                    .when(alarmService).sendRequestRejectedEmail(any(), anyString());

            RejectRequestDTO dto = new RejectRequestDTO(38L, "신청서 양식 미흡");

            // 이메일 실패해도 예외 미전파 (DB 롤백 없음)
            service.rejectRequest(dto);

            verify(request).reject("신청서 양식 미흡");
        }

        @Test
        @DisplayName("이메일 발송 실패 시에도 정상 응답 DTO가 반환된다")
        void rejectRequest_emailFailure_stillReturnsResponseDTO() {
            buildMockedRequestWithStatus(39L, Status.PENDING);
            doThrow(new RuntimeException("MessageUtils 키 없음"))
                    .when(alarmService).sendRequestRejectedEmail(any(), anyString());

            RejectRequestDTO dto = new RejectRequestDTO(39L, "사유");

            // 예외 없이 DTO 반환
            assertThat(service.rejectRequest(dto)).isNotNull();
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
            when(changeRequestRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(changeRequest));
            when(userRepository.findById(100L)).thenReturn(Optional.of(mockUser));

            RejectModificationDTO dto = new RejectModificationDTO(1L, "변경 사유 불충분");
            service.rejectModification(100L, dto);

            verify(changeRequest).deny(mockUser, "변경 사유 불충분");
        }

        @Test
        @DisplayName("존재하지 않는 변경 요청 ID로 거절하면 BusinessException을 던진다")
        void rejectModification_throwsException_whenChangeRequestNotFound() {
            when(changeRequestRepository.findByIdForUpdate(999L)).thenReturn(Optional.empty());

            RejectModificationDTO dto = new RejectModificationDTO(999L, "사유");

            assertThatThrownBy(() -> service.rejectModification(100L, dto))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("PENDING 이 아닌 상태의 변경 요청 거절 시 BusinessException을 던진다")
        void rejectModification_throwsException_whenStatusIsNotPending() {
            ChangeRequest changeRequest = mock(ChangeRequest.class);
            when(changeRequest.getStatus()).thenReturn(Status.FULFILLED);
            when(changeRequestRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(changeRequest));

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
            when(changeRequestRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(changeRequest));
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            RejectModificationDTO dto = new RejectModificationDTO(3L, "사유");

            assertThatThrownBy(() -> service.rejectModification(999L, dto))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("변경 요청 거절 성공 시 alarmService.sendModificationRejectedEmail이 호출된다")
        void rejectModification_sendsRejectionEmail_onSuccess() {
            ChangeRequest changeRequest = mock(ChangeRequest.class);
            when(changeRequest.getStatus()).thenReturn(Status.PENDING);
            when(changeRequestRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(changeRequest));
            when(userRepository.findById(100L)).thenReturn(Optional.of(mockUser));

            RejectModificationDTO dto = new RejectModificationDTO(10L, "변경 사유 불충분");
            service.rejectModification(100L, dto);

            verify(alarmService).sendModificationRejectedEmail(changeRequest, "변경 사유 불충분");
        }

        @Test
        @DisplayName("거절 사유가 이메일 발송 메서드에 그대로 전달된다")
        void rejectModification_passesAdminCommentToEmail() {
            ChangeRequest changeRequest = mock(ChangeRequest.class);
            when(changeRequest.getStatus()).thenReturn(Status.PENDING);
            when(changeRequestRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(changeRequest));
            when(userRepository.findById(100L)).thenReturn(Optional.of(mockUser));
            String comment = "리소스 여유 없음";

            service.rejectModification(100L, new RejectModificationDTO(11L, comment));

            verify(alarmService).sendModificationRejectedEmail(changeRequest, comment);
        }

        @Test
        @DisplayName("PENDING이 아닌 상태에서 거절 시 이메일이 발송되지 않는다")
        void rejectModification_doesNotSendEmail_whenStatusIsNotPending() {
            ChangeRequest changeRequest = mock(ChangeRequest.class);
            when(changeRequest.getStatus()).thenReturn(Status.FULFILLED);
            when(changeRequestRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(changeRequest));

            assertThatThrownBy(() -> service.rejectModification(100L, new RejectModificationDTO(12L, "사유")))
                    .isInstanceOf(BusinessException.class);

            verify(alarmService, never()).sendModificationRejectedEmail(any(), anyString());
        }

        @Test
        @DisplayName("이메일 발송 실패 시 예외가 전파되지 않고 deny()는 이미 호출된 상태다")
        void rejectModification_emailFailure_doesNotPropagateException() {
            ChangeRequest changeRequest = mock(ChangeRequest.class);
            when(changeRequest.getStatus()).thenReturn(Status.PENDING);
            when(changeRequestRepository.findByIdForUpdate(13L)).thenReturn(Optional.of(changeRequest));
            when(userRepository.findById(100L)).thenReturn(Optional.of(mockUser));
            doThrow(new RuntimeException("SMTP 연결 실패"))
                    .when(alarmService).sendModificationRejectedEmail(any(), anyString());

            // 이메일 실패해도 예외 미전파 (DB 롤백 없음)
            service.rejectModification(100L, new RejectModificationDTO(13L, "변경 사유 불충분"));

            verify(changeRequest).deny(mockUser, "변경 사유 불충분");
        }

        @Test
        @DisplayName("이메일 발송 시 RuntimeException 발생해도 deny()는 호출되고 정상 종료된다")
        void rejectModification_emailThrowsRuntimeException_denyStillCalled() {
            ChangeRequest changeRequest = mock(ChangeRequest.class);
            when(changeRequest.getStatus()).thenReturn(Status.PENDING);
            when(changeRequestRepository.findByIdForUpdate(14L)).thenReturn(Optional.of(changeRequest));
            when(userRepository.findById(100L)).thenReturn(Optional.of(mockUser));
            doThrow(new IllegalStateException("MessageUtils 키 없음"))
                    .when(alarmService).sendModificationRejectedEmail(any(), anyString());

            service.rejectModification(100L, new RejectModificationDTO(14L, "사유"));

            verify(changeRequest).deny(eq(mockUser), eq("사유"));
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // approveModification 테스트
    // ──────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("approveModification")
    class ApproveModification {

        @Test
        @DisplayName("EXPIRES_AT 변경 요청 승인 시 이전 만료일 캡처 후 updateExpiresAt()가 호출된다")
        void approveModification_expiresAt_success() throws Exception {
            LocalDateTime oldExpiry = LocalDateTime.of(2026, 6, 30, 23, 59, 59);
            LocalDateTime newExpiry = LocalDateTime.of(2027, 12, 31, 23, 59, 59);

            ChangeRequest changeRequest = mock(ChangeRequest.class);
            Request originalRequest = buildMockedRequestWithStatus(21L, Status.FULFILLED);
            when(originalRequest.getExpiresAt()).thenReturn(oldExpiry);
            when(changeRequest.getStatus()).thenReturn(Status.PENDING);
            when(changeRequest.getChangeType()).thenReturn(ChangeType.EXPIRES_AT);
            when(changeRequest.getNewValue()).thenReturn("\"2027-12-31T23:59:59\"");
            when(changeRequest.getRequest()).thenReturn(originalRequest);
            when(changeRequestRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(changeRequest));
            when(userRepository.findById(100L)).thenReturn(Optional.of(mockUser));

            ApproveModificationDTO dto = new ApproveModificationDTO(2L, "기간 연장 승인");
            service.approveModification(100L, dto);

            ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
            verify(originalRequest).updateExpiresAt(captor.capture());
            assertThat(captor.getValue()).isEqualTo(newExpiry);
            verify(changeRequest).approve(mockUser, "기간 연장 승인");
            verify(alarmService).sendContainerExtendedEmail(eq(originalRequest), eq(oldExpiry), eq(newExpiry));
            // EXPIRES_AT은 전용 메일(sendContainerExtendedEmail)만 보내고, 공용 승인 메일은 중복 발송하지 않는다
            verify(alarmService, never()).sendModificationApprovedEmail(any(), any());
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
            when(changeRequestRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(changeRequest));
            when(userRepository.findById(100L)).thenReturn(Optional.of(mockUser));
            when(resourceGroupRepository.findById(2)).thenReturn(Optional.of(newRg));

            ApproveModificationDTO dto = new ApproveModificationDTO(3L, "리소스 그룹 변경 승인");
            service.approveModification(100L, dto);

            verify(originalRequest).updateResourceGroup(newRg);
            verify(changeRequest).approve(mockUser, "리소스 그룹 변경 승인");
            verify(alarmService).sendModificationApprovedEmail(changeRequest, "리소스 그룹 변경 승인");
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
            when(changeRequestRepository.findByIdForUpdate(4L)).thenReturn(Optional.of(changeRequest));
            when(userRepository.findById(100L)).thenReturn(Optional.of(mockUser));
            when(containerImageRepository.findById(5L)).thenReturn(Optional.of(newImage));

            ApproveModificationDTO dto = new ApproveModificationDTO(4L, "이미지 변경 승인");
            service.approveModification(100L, dto);

            verify(originalRequest).updateContainerImage(newImage);
            verify(changeRequest).approve(mockUser, "이미지 변경 승인");
            verify(alarmService).sendModificationApprovedEmail(changeRequest, "이미지 변경 승인");
        }

        @Test
        @DisplayName("GROUP 변경 요청 승인 시 originalRequest.addGroup()이 호출된다")
        void approveModification_group_success() throws Exception {
            ChangeRequest changeRequest = mock(ChangeRequest.class);
            Request originalRequest = buildMockedRequestWithStatus(25L, Status.FULFILLED);
            Group newGroup = mock(Group.class);
            when(newGroup.getUbuntuGid()).thenReturn(42L);
            when(changeRequest.getStatus()).thenReturn(Status.PENDING);
            when(changeRequest.getChangeType()).thenReturn(ChangeType.GROUP);
            when(changeRequest.getNewValue()).thenReturn("[42]");
            when(changeRequest.getRequest()).thenReturn(originalRequest);
            when(changeRequestRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(changeRequest));
            when(userRepository.findById(100L)).thenReturn(Optional.of(mockUser));
            when(groupRepository.findByUbuntuGid(42L)).thenReturn(Optional.of(newGroup));

            ApproveModificationDTO dto = new ApproveModificationDTO(9L, "그룹 변경 승인");
            service.approveModification(100L, dto);

            verify(originalRequest).addGroup(newGroup);
            verify(changeRequest).approve(mockUser, "그룹 변경 승인");
            verify(alarmService).sendModificationApprovedEmail(changeRequest, "그룹 변경 승인");
        }

        @Test
        @DisplayName("PORT 변경 요청 승인 시 요청된 각 포트에 대해 portRequestService.createPortRequest()가 호출된다")
        void approveModification_port_success() throws Exception {
            ChangeRequest changeRequest = mock(ChangeRequest.class);
            Request originalRequest = buildMockedRequestWithStatus(24L, Status.FULFILLED);
            when(originalRequest.getResourceGroup()).thenReturn(mockRg);
            when(changeRequest.getStatus()).thenReturn(Status.PENDING);
            when(changeRequest.getChangeType()).thenReturn(ChangeType.PORT);
            when(changeRequest.getNewValue()).thenReturn(
                    "[{\"internalPort\":3000,\"usagePurpose\":\"웹 서버\"},{\"internalPort\":6006,\"usagePurpose\":\"텐서보드\"}]");
            when(changeRequest.getRequest()).thenReturn(originalRequest);
            when(changeRequestRepository.findByIdForUpdate(8L)).thenReturn(Optional.of(changeRequest));
            when(userRepository.findById(100L)).thenReturn(Optional.of(mockUser));

            ApproveModificationDTO dto = new ApproveModificationDTO(8L, "포트 추가 승인");
            service.approveModification(100L, dto);

            ArgumentCaptor<Integer> portCaptor = ArgumentCaptor.forClass(Integer.class);
            ArgumentCaptor<String> purposeCaptor = ArgumentCaptor.forClass(String.class);
            verify(portRequestService, times(2)).createPortRequest(
                    eq(originalRequest), eq(mockRg), portCaptor.capture(), purposeCaptor.capture());

            assertThat(portCaptor.getAllValues()).containsExactly(3000, 6006);
            assertThat(purposeCaptor.getAllValues()).containsExactly("웹 서버", "텐서보드");
            verify(changeRequest).approve(mockUser, "포트 추가 승인");
            verify(alarmService).sendModificationApprovedEmail(changeRequest, "포트 추가 승인");
        }

        @Test
        @DisplayName("변경 값 JSON 파싱 실패 시 INTERNAL_SERVER_ERROR로 감싸서 던지고 상태를 변경하지 않는다")
        void approveModification_invalidJson_wrapsAsInternalServerErrorAndDoesNotApply() throws Exception {
            ChangeRequest changeRequest = mock(ChangeRequest.class);
            Request originalRequest = buildMockedRequestWithStatus(26L, Status.FULFILLED);
            when(changeRequest.getStatus()).thenReturn(Status.PENDING);
            when(changeRequest.getChangeType()).thenReturn(ChangeType.EXPIRES_AT);
            when(changeRequest.getNewValue()).thenReturn("이건-날짜가-아님");
            when(changeRequest.getRequest()).thenReturn(originalRequest);
            when(changeRequestRepository.findByIdForUpdate(15L)).thenReturn(Optional.of(changeRequest));
            when(userRepository.findById(100L)).thenReturn(Optional.of(mockUser));

            ApproveModificationDTO dto = new ApproveModificationDTO(15L, "승인");

            assertThatThrownBy(() -> service.approveModification(100L, dto))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR);

            verify(originalRequest, never()).updateExpiresAt(any());
            verify(changeRequest, never()).approve(any(), anyString());
            verify(alarmService, never()).sendModificationApprovedEmail(any(), any());
        }

        @Test
        @DisplayName("존재하지 않는 변경 요청 ID로 승인하면 BusinessException을 던진다")
        void approveModification_throwsException_whenChangeRequestNotFound() {
            when(changeRequestRepository.findByIdForUpdate(999L)).thenReturn(Optional.empty());

            ApproveModificationDTO dto = new ApproveModificationDTO(999L, "승인");

            assertThatThrownBy(() -> service.approveModification(100L, dto))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("PENDING이 아닌 변경 요청 승인 시 BusinessException을 던진다")
        void approveModification_throwsException_whenNotPendingStatus() {
            ChangeRequest changeRequest = mock(ChangeRequest.class);
            when(changeRequest.getStatus()).thenReturn(Status.FULFILLED);
            when(changeRequestRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(changeRequest));

            ApproveModificationDTO dto = new ApproveModificationDTO(5L, "승인");

            assertThatThrownBy(() -> service.approveModification(100L, dto))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("존재하지 않는 adminId로 승인하면 BusinessException을 던진다")
        void approveModification_throwsException_whenAdminNotFound() {
            ChangeRequest changeRequest = mock(ChangeRequest.class);
            when(changeRequest.getStatus()).thenReturn(Status.PENDING);
            when(changeRequestRepository.findByIdForUpdate(6L)).thenReturn(Optional.of(changeRequest));
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
            when(changeRequestRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(changeRequest));
            when(userRepository.findById(100L)).thenReturn(Optional.of(mockUser));

            ApproveModificationDTO dto = new ApproveModificationDTO(7L, "승인");

            assertThatThrownBy(() -> service.approveModification(100L, dto))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("originalRequest가 FULFILLED가 아니면(DELETED 등) 필드를 건드리지 않고 BusinessException을 던진다")
        void approveModification_originalRequestNotFulfilled_throwsAndDoesNotApply() {
            ChangeRequest changeRequest = mock(ChangeRequest.class);
            Request originalRequest = buildMockedRequestWithStatus(22L, Status.DELETED);
            when(changeRequest.getStatus()).thenReturn(Status.PENDING);
            when(changeRequest.getChangeType()).thenReturn(ChangeType.EXPIRES_AT);
            when(changeRequest.getRequest()).thenReturn(originalRequest);
            when(changeRequestRepository.findByIdForUpdate(8L)).thenReturn(Optional.of(changeRequest));
            when(userRepository.findById(100L)).thenReturn(Optional.of(mockUser));

            ApproveModificationDTO dto = new ApproveModificationDTO(8L, "승인");

            assertThatThrownBy(() -> service.approveModification(100L, dto))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_REQUEST_STATUS);

            verify(originalRequest, never()).updateExpiresAt(any());
            verify(changeRequest, never()).approve(any(), any());
        }
    }

    private Request buildMockedRequestWithStatus(Long requestId, Status status) {
        Request request = mock(Request.class);
        when(request.getRequestId()).thenReturn(requestId);
        when(request.getStatus()).thenReturn(status);
        when(request.getUbuntuUsername()).thenReturn("testuser");
        when(request.getUbuntuPassword()).thenReturn("encoded_pw");
        when(request.getUbuntuPasswordBase64()).thenReturn("cGxhaW5fdGV4dF9wdw==");
        when(request.getRequestGroups()).thenReturn(new LinkedHashSet<>());
        when(request.getUser()).thenReturn(mockUser);
        when(request.getResourceGroup()).thenReturn(mockRg);
        when(request.getContainerImage()).thenReturn(mockImage);
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(requestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));
        return request;
    }
}
