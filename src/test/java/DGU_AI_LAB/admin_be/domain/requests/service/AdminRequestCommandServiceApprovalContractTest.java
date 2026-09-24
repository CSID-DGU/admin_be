package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.domain.alarm.service.AlarmService;
import DGU_AI_LAB.admin_be.domain.containerImage.entity.ContainerImage;
import DGU_AI_LAB.admin_be.domain.containerImage.repository.ContainerImageRepository;
import DGU_AI_LAB.admin_be.domain.groups.repository.GroupRepository;
import DGU_AI_LAB.admin_be.domain.groups.service.GroupService;
import DGU_AI_LAB.admin_be.domain.pod.repository.PodExternalPortRepository;
import DGU_AI_LAB.admin_be.domain.portRequests.service.PortRequestService;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.ApproveRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.ProvisionRegisterRequestDTO;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.util.LinkedHashSet;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 승인 경로(approveRequest)가 밖에서 관찰되는 호출과 상태 전이를 어떤 순서로 하는지 고정한다.
 * VASC 실험은 admin_be 의 요청 상태를 Operational Baseline 의 시스템 선언으로 읽으므로,
 * 선언 시점과 작업 시작 시점의 앞뒤가 뒤집히면 측정값이 조용히 달라진다.
 *
 * <p>동작(인자·반환값) 검증은 {@code AdminRequestCommandServiceTest} 가 맡는다. 여기서는
 * 순서만 본다. 트랜잭션 템플릿을 몇 번 쓰는지 같은 내부 구현은 고정하지 않는다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("approveRequest 호출 순서 계약")
class AdminRequestCommandServiceApprovalContractTest {

    @Mock private AlarmService alarmService;
    @Mock private RequestRepository requestRepository;
    @Mock private UserRepository userRepository;
    @Mock private ContainerImageRepository containerImageRepository;
    @Mock private ResourceGroupRepository resourceGroupRepository;
    @Mock private ChangeRequestRepository changeRequestRepository;
    @Mock private GroupRepository groupRepository;
    @Mock private GroupService groupService;
    @Mock private PodExternalPortRepository podExternalPortRepository;
    @Mock private OperationJobService operationJobService;
    @Mock private PortRequestService portRequestService;
    @Mock private PlatformTransactionManager transactionManager;
    @Mock private TransactionStatus transactionStatus;

    @Mock private ContainerImage mockImage;
    @Mock private ResourceGroup mockRg;
    @Mock private User mockUser;

    private AdminRequestCommandService service;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);

        service = new AdminRequestCommandService(
                alarmService, requestRepository, userRepository, containerImageRepository,
                resourceGroupRepository, changeRequestRepository,
                groupRepository, groupService, podExternalPortRepository, operationJobService,
                portRequestService, new ObjectMapper(), transactionManager
        );

        when(mockUser.getUserId()).thenReturn(100L);
        when(mockUser.getName()).thenReturn("테스트유저");
        when(mockUser.hasUbuntuAccount()).thenReturn(false);
        when(userRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(mockUser));
        when(containerImageRepository.findById(1L)).thenReturn(Optional.of(mockImage));
        when(resourceGroupRepository.findById(1)).thenReturn(Optional.of(mockRg));
        when(mockRg.getServerName()).thenReturn("farm2");
    }

    /**
     * 승인 대상 신청의 mock 을 만든다. 상태는 순서대로 PENDING 과 PROCESSING 을 돌려준다.
     * 승인 시작 지점에서 PENDING 을 확인하고, 보상 경로가 다시 물어볼 때 PROCESSING 을 보기 때문이다.
     */
    private Request approvableRequest(Long requestId) {
        Request request = mock(Request.class);
        when(request.getRequestId()).thenReturn(requestId);
        when(request.getStatus()).thenReturn(Status.PENDING, Status.PROCESSING);
        when(request.getUbuntuUsername()).thenReturn("testuser");
        when(mockUser.getUbuntuPasswordHash()).thenReturn("$6$salt$hash");
        when(request.getRequestGroups()).thenReturn(new LinkedHashSet<>());
        when(request.getUser()).thenReturn(mockUser);
        when(request.getResourceGroup()).thenReturn(mockRg);
        when(request.getContainerImage()).thenReturn(mockImage);
        when(requestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));
        return request;
    }

    @Test
    @DisplayName("계정을 새로 만드는 승인은 신청을 잠그고 PROCESSING 으로 바꾼 뒤에 작업을 등록한다")
    void newAccountApprovalLocksAndMarksProcessingBeforeRegistering() {
        Long requestId = 301L;
        Request request = approvableRequest(requestId);

        service.approveRequest(new ApproveRequestDTO(requestId, 1L, 1, "승인합니다"));

        InOrder order = inOrder(requestRepository, request, operationJobService);
        order.verify(requestRepository).findByIdForUpdate(requestId);
        order.verify(request).markAsProcessing();
        order.verify(request).prepareAsyncApproval(mockImage, mockRg, "승인합니다");
        order.verify(operationJobService).registerProvision(any(ProvisionRegisterRequestDTO.class));

        // 계정을 새로 만드는 경로에서는 생성 작업이 그룹까지 함께 넣으므로 여기서 더하지 않는다.
        verify(groupService, never()).addUserToGroups(anyString(), anyList());
    }

    @Test
    @DisplayName("등록한 작업 번호를 신청에 남겨, 결과 폴러가 이전 작업의 결과를 반영하지 않게 한다")
    void recordsRegisteredJobId() {
        Long requestId = 303L;
        Request request = approvableRequest(requestId);
        when(operationJobService.registerProvision(any(ProvisionRegisterRequestDTO.class))).thenReturn(3616L);

        service.approveRequest(new ApproveRequestDTO(requestId, 1L, 1, null));

        InOrder order = inOrder(operationJobService, request);
        order.verify(operationJobService).registerProvision(any(ProvisionRegisterRequestDTO.class));
        order.verify(request).recordProvisionJob(3616L);
    }

    @Test
    @DisplayName("계정을 재사용하는 승인은 그룹 정보를 작업 등록 DTO에 실어 보내고 로컬로는 그룹을 넣지 않는다")
    void reusedAccountApprovalSendsGroupsWithRegistration() {
        Long requestId = 302L;
        Request request = approvableRequest(requestId);
        when(mockUser.hasUbuntuAccount()).thenReturn(true);

        service.approveRequest(new ApproveRequestDTO(requestId, 1L, 1, null));

        ArgumentCaptor<ProvisionRegisterRequestDTO> captor = ArgumentCaptor.forClass(ProvisionRegisterRequestDTO.class);
        InOrder order = inOrder(request, operationJobService);
        order.verify(request).prepareAsyncApproval(mockImage, mockRg, null);
        order.verify(operationJobService).registerProvision(captor.capture());
        assertThat(captor.getValue().account()).isNull();

        // config-server의 provision 제어기가 Pod 생성 후 그룹을 추가하므로 로컬에서는 호출하지 않는다.
        verify(groupService, never()).addUserToGroups(anyString(), anyList());
    }

    @Test
    @DisplayName("작업 등록이 실패하면 관리자에게 먼저 알리고 그다음에 신청을 PENDING 으로 되돌리며, 예외는 그대로 전파한다")
    void registrationFailureNotifiesBeforeReverting() {
        Long requestId = 303L;
        Request request = approvableRequest(requestId);
        doThrow(new BusinessException(ErrorCode.POD_CREATION_FAILED))
                .when(operationJobService).registerProvision(any());

        assertThatThrownBy(() -> service.approveRequest(new ApproveRequestDTO(requestId, 1L, 1, null)))
                .isInstanceOf(BusinessException.class);

        InOrder order = inOrder(operationJobService, alarmService, request);
        order.verify(operationJobService).registerProvision(any(ProvisionRegisterRequestDTO.class));
        order.verify(alarmService).sendAdminSlackNotification(eq("farm2"), anyString());
        order.verify(request).revertToPending();
    }

    @Test
    @DisplayName("승인 경로는 완료를 선언하지 않는다")
    void approvalNeverDeclaresCompletion() {
        Long requestId = 304L;
        Request request = approvableRequest(requestId);

        service.approveRequest(new ApproveRequestDTO(requestId, 1L, 1, "승인합니다"));

        // 완료 선언은 작업이 성공한 뒤 completeApprovalJob 에서만 일어난다.
        verify(request, never()).completeApproval();
    }
}
