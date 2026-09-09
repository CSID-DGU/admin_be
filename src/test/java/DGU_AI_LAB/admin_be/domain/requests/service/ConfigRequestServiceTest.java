package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.domain.containerImage.entity.ContainerImage;
import DGU_AI_LAB.admin_be.domain.groups.entity.Group;
import DGU_AI_LAB.admin_be.domain.nodes.entity.Node;
import DGU_AI_LAB.admin_be.domain.nodes.repository.NodeRepository;
import DGU_AI_LAB.admin_be.domain.portRequests.entity.PortRequests;
import DGU_AI_LAB.admin_be.domain.portRequests.repository.PortRequestRepository;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.AcceptInfoResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.RequestGroup;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfigRequestServiceTest {

    @Mock private RequestRepository requestRepository;
    @Mock private UserRepository userRepository;
    @Mock private PortRequestRepository portRequestRepository;
    @Mock private NodeRepository nodeRepository;

    private ConfigRequestService service;

    @BeforeEach
    void setUp() {
        service = new ConfigRequestService(requestRepository, userRepository, portRequestRepository, nodeRepository);
    }

    private Request mockRequest(long requestId, String username, ResourceGroup resourceGroup) {
        ContainerImage image = mock(ContainerImage.class);
        when(image.getImageName()).thenReturn("cuda");
        when(image.getImageVersion()).thenReturn("11.8");

        Request request = mock(Request.class);
        when(request.getRequestId()).thenReturn(requestId);
        when(request.getUbuntuUsername()).thenReturn(username);
        when(request.getContainerImage()).thenReturn(image);
        when(request.getRequestGroups()).thenReturn(new LinkedHashSet<>());
        when(request.getResourceGroup()).thenReturn(resourceGroup);

        when(requestRepository.findByUbuntuUsernameAndStatusInOrderByRequestIdDesc(username, Status.openStatuses()))
                .thenReturn(List.of(request));
        when(portRequestRepository.findByRequestRequestId(requestId)).thenReturn(List.of());
        when(nodeRepository.findAllByResourceGroup(resourceGroup)).thenReturn(List.of());

        return request;
    }

    @Test
    @DisplayName("username 가용성은 신청이 아니라 웹 계정(User) 기준으로 판단한다")
    void isUbuntuUsernameAvailable_checksWebAccountsNotRequests() {
        when(userRepository.existsByUbuntuUsername("taken")).thenReturn(true);
        when(userRepository.existsByUbuntuUsername("free")).thenReturn(false);

        assertThat(service.isUbuntuUsernameAvailable("taken")).isFalse();
        assertThat(service.isUbuntuUsernameAvailable("free")).isTrue();
        // 종료된 신청 이력에 같은 유저네임이 남아 있어도 가용성 판단에 끼어들면 안 된다.
        verifyNoInteractions(requestRepository);
    }

    @Test
    @DisplayName("groups 필드는 GID와 그룹명을 함께 반환한다")
    void getAcceptInfo_returnsGroupsWithGidAndName() {
        // Given
        String username = "testuser";
        ResourceGroup resourceGroup = mock(ResourceGroup.class);

        ContainerImage image = mock(ContainerImage.class);
        when(image.getImageName()).thenReturn("cuda");
        when(image.getImageVersion()).thenReturn("11.8");

        Group group1 = mock(Group.class);
        when(group1.getUbuntuGid()).thenReturn(10004L);
        when(group1.getGroupName()).thenReturn("hyrn");

        Group group2 = mock(Group.class);
        when(group2.getUbuntuGid()).thenReturn(2001L);
        when(group2.getGroupName()).thenReturn("ailab");

        RequestGroup rg1 = mock(RequestGroup.class);
        when(rg1.getGroup()).thenReturn(group1);

        RequestGroup rg2 = mock(RequestGroup.class);
        when(rg2.getGroup()).thenReturn(group2);

        Request request = mock(Request.class);
        when(request.getRequestId()).thenReturn(1L);
        when(request.getUbuntuUsername()).thenReturn(username);
        when(request.getContainerImage()).thenReturn(image);
        when(request.getRequestGroups()).thenReturn(new LinkedHashSet<>(Set.of(rg1, rg2)));
        when(request.getResourceGroup()).thenReturn(resourceGroup);

        when(requestRepository.findByUbuntuUsernameAndStatusInOrderByRequestIdDesc(username, Status.openStatuses()))
                .thenReturn(List.of(request));
        when(portRequestRepository.findByRequestRequestId(1L)).thenReturn(List.of());
        when(nodeRepository.findAllByResourceGroup(resourceGroup)).thenReturn(List.of());

        // When
        AcceptInfoResponseDTO result = service.getAcceptInfo(username);

        // Then
        assertThat(result.groups()).hasSize(2);
        assertThat(result.groups()).extracting(AcceptInfoResponseDTO.GroupDTO::gid)
                .containsExactlyInAnyOrder(10004L, 2001L);
        assertThat(result.groups()).extracting(AcceptInfoResponseDTO.GroupDTO::name)
                .containsExactlyInAnyOrder("hyrn", "ailab");
    }

    @Test
    @DisplayName("gpu_nodes는 노드명, GPU 수, k8s 포맷 CPU/메모리 제한을 반환한다")
    void getAcceptInfo_returnsGpuNodesWithK8sFormat() {
        // Given
        String username = "testuser";
        ResourceGroup resourceGroup = mock(ResourceGroup.class);

        Node node = mock(Node.class);
        when(node.getNodeId()).thenReturn("farm2");
        when(node.getNumberGpu()).thenReturn(2);
        when(node.getCpuCoreCount()).thenReturn(4);
        when(node.getMemorySizeGB()).thenReturn(8);

        ContainerImage image = mock(ContainerImage.class);
        when(image.getImageName()).thenReturn("cuda");
        when(image.getImageVersion()).thenReturn("11.8");

        Request request = mock(Request.class);
        when(request.getRequestId()).thenReturn(2L);
        when(request.getUbuntuUsername()).thenReturn(username);
        when(request.getContainerImage()).thenReturn(image);
        when(request.getRequestGroups()).thenReturn(new LinkedHashSet<>());
        when(request.getResourceGroup()).thenReturn(resourceGroup);

        when(requestRepository.findByUbuntuUsernameAndStatusInOrderByRequestIdDesc(username, Status.openStatuses()))
                .thenReturn(List.of(request));
        when(portRequestRepository.findByRequestRequestId(2L)).thenReturn(List.of());
        when(nodeRepository.findAllByResourceGroup(resourceGroup)).thenReturn(List.of(node));

        // When
        AcceptInfoResponseDTO result = service.getAcceptInfo(username);

        // Then
        assertThat(result.gpu_nodes()).hasSize(1);
        AcceptInfoResponseDTO.GpuNodeDTO gpuNode = result.gpu_nodes().get(0);
        assertThat(gpuNode.node_name()).isEqualTo("farm2");
        assertThat(gpuNode.num_gpu()).isEqualTo(2);
        assertThat(gpuNode.cpu_limit()).isEqualTo("4000m");
        assertThat(gpuNode.memory_limit()).isEqualTo("8192Mi");
    }

    @Test
    @DisplayName("getAcceptInfo는 PortRequests(additional ports)를 additional_ports로 반환한다")
    void getAcceptInfo_returnsAdditionalPortsFromPortRequests() {
        // Given
        String username = "testuser";
        ResourceGroup resourceGroup = mock(ResourceGroup.class);

        ContainerImage image = mock(ContainerImage.class);
        when(image.getImageName()).thenReturn("containerssh-guest");
        when(image.getImageVersion()).thenReturn("ubuntu22.04");

        Request request = mock(Request.class);
        when(request.getRequestId()).thenReturn(1L);
        when(request.getUbuntuUsername()).thenReturn(username);
        when(request.getContainerImage()).thenReturn(image);
        when(request.getRequestGroups()).thenReturn(new LinkedHashSet<>());
        when(request.getResourceGroup()).thenReturn(resourceGroup);

        when(requestRepository.findByUbuntuUsernameAndStatusInOrderByRequestIdDesc(username, Status.openStatuses()))
                .thenReturn(List.of(request));
        when(nodeRepository.findAllByResourceGroup(resourceGroup)).thenReturn(List.of());

        PortRequests tensorboard = mock(PortRequests.class);
        when(tensorboard.getInternalPort()).thenReturn(6006);
        when(tensorboard.getUsagePurpose()).thenReturn("tensorboard");

        PortRequests webapp = mock(PortRequests.class);
        when(webapp.getInternalPort()).thenReturn(8080);
        when(webapp.getUsagePurpose()).thenReturn("webapp");

        when(portRequestRepository.findByRequestRequestId(1L))
                .thenReturn(List.of(tensorboard, webapp));

        // When
        AcceptInfoResponseDTO result = service.getAcceptInfo(username);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.additional_ports()).hasSize(2);

        AcceptInfoResponseDTO.AdditionalPortDTO first = result.additional_ports().get(0);
        assertThat(first.internal_port()).isEqualTo(6006);
        assertThat(first.usage_purpose()).isEqualTo("tensorboard");

        AcceptInfoResponseDTO.AdditionalPortDTO second = result.additional_ports().get(1);
        assertThat(second.internal_port()).isEqualTo(8080);
        assertThat(second.usage_purpose()).isEqualTo("webapp");
    }

    @Test
    @DisplayName("additional_ports에는 external_port 정보가 포함되지 않는다")
    void getAcceptInfo_additionalPortsDoNotContainExternalPort() {
        // Given
        String username = "testuser2";
        ResourceGroup resourceGroup = mock(ResourceGroup.class);

        ContainerImage image = mock(ContainerImage.class);
        when(image.getImageName()).thenReturn("cuda");
        when(image.getImageVersion()).thenReturn("11.8");

        Request request = mock(Request.class);
        when(request.getRequestId()).thenReturn(2L);
        when(request.getUbuntuUsername()).thenReturn(username);
        when(request.getContainerImage()).thenReturn(image);
        when(request.getRequestGroups()).thenReturn(new LinkedHashSet<>());
        when(request.getResourceGroup()).thenReturn(resourceGroup);

        when(requestRepository.findByUbuntuUsernameAndStatusInOrderByRequestIdDesc(username, Status.openStatuses()))
                .thenReturn(List.of(request));
        when(nodeRepository.findAllByResourceGroup(resourceGroup)).thenReturn(List.of());

        PortRequests portReq = mock(PortRequests.class);
        when(portReq.getInternalPort()).thenReturn(6006);
        when(portReq.getUsagePurpose()).thenReturn("tensorboard");

        when(portRequestRepository.findByRequestRequestId(2L))
                .thenReturn(List.of(portReq));

        // When
        AcceptInfoResponseDTO result = service.getAcceptInfo(username);

        // Then
        assertThat(result.additional_ports()).hasSize(1);
        AcceptInfoResponseDTO.AdditionalPortDTO portDTO = result.additional_ports().get(0);
        assertThat(portDTO.internal_port()).isEqualTo(6006);
        assertThat(portDTO.usage_purpose()).isEqualTo("tensorboard");
    }

    @Test
    @DisplayName("additional_ports가 없으면 빈 리스트를 반환한다")
    void getAcceptInfo_noAdditionalPorts_returnsEmptyList() {
        // Given
        String username = "testuser3";
        ResourceGroup resourceGroup = mock(ResourceGroup.class);
        mockRequest(3L, username, resourceGroup);

        // When
        AcceptInfoResponseDTO result = service.getAcceptInfo(username);

        // Then
        assertThat(result.additional_ports()).isEmpty();
    }

    @ParameterizedTest(name = "status={0}")
    @EnumSource(value = Status.class, names = {"PENDING", "PROCESSING", "FULFILLED", "MIGRATING", "EXPIRING"})
    @DisplayName("getAcceptInfoByRequestId는 살아있는(openStatuses) 신청이면 정보를 반환한다")
    void getAcceptInfoByRequestId_returnsInfo_whenStatusIsOpen(Status status) {
        ResourceGroup resourceGroup = mock(ResourceGroup.class);
        ContainerImage image = mock(ContainerImage.class);
        when(image.getImageName()).thenReturn("cuda");
        when(image.getImageVersion()).thenReturn("11.8");

        Request request = mock(Request.class);
        when(request.getRequestId()).thenReturn(5L);
        when(request.getStatus()).thenReturn(status);
        when(request.getContainerImage()).thenReturn(image);
        when(request.getRequestGroups()).thenReturn(new LinkedHashSet<>());
        when(request.getResourceGroup()).thenReturn(resourceGroup);

        when(requestRepository.findById(5L)).thenReturn(Optional.of(request));
        when(portRequestRepository.findByRequestRequestId(5L)).thenReturn(List.of());
        when(nodeRepository.findAllByResourceGroup(resourceGroup)).thenReturn(List.of());

        AcceptInfoResponseDTO result = service.getAcceptInfoByRequestId(5L);

        assertThat(result).isNotNull();
    }

    @ParameterizedTest(name = "status={0}")
    @EnumSource(value = Status.class, names = {"DENIED", "DELETED"})
    @DisplayName("취소/만료로 종료된 신청은 requestId로 조회해도 승인 정보를 내주지 않는다 — config-server가 끝난 신청을 근거로 인프라를 구성하면 안 된다")
    void getAcceptInfoByRequestId_throws_whenStatusIsTerminal(Status status) {
        Request request = mock(Request.class);
        when(request.getStatus()).thenReturn(status);
        when(requestRepository.findById(6L)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.getAcceptInfoByRequestId(6L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_APPROVAL_NOT_FOUND);

        verifyNoInteractions(portRequestRepository, nodeRepository);
    }

    @Test
    @DisplayName("존재하지 않는 requestId는 USER_APPROVAL_NOT_FOUND를 던진다")
    void getAcceptInfoByRequestId_throws_whenNotFound() {
        when(requestRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getAcceptInfoByRequestId(99L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_APPROVAL_NOT_FOUND);
    }

}
