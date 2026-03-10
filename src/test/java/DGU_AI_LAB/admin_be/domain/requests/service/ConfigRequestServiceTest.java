package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.domain.containerImage.entity.ContainerImage;
import DGU_AI_LAB.admin_be.domain.nodes.entity.Node;
import DGU_AI_LAB.admin_be.domain.nodes.repository.NodeRepository;
import DGU_AI_LAB.admin_be.domain.portRequests.entity.PortRequests;
import DGU_AI_LAB.admin_be.domain.portRequests.repository.PortRequestRepository;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.AcceptInfoResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup;
import DGU_AI_LAB.admin_be.domain.usedIds.entity.UsedId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfigRequestServiceTest {

    @Mock private RequestRepository requestRepository;
    @Mock private NodeRepository nodeRepository;
    @Mock private PortRequestRepository portRequestRepository;

    private ConfigRequestService service;

    @BeforeEach
    void setUp() {
        service = new ConfigRequestService(requestRepository, nodeRepository, portRequestRepository);
    }

    @Test
    @DisplayName("getAcceptInfo는 PortRequests(additional ports)를 additional_ports로 반환한다")
    void getAcceptInfo_returnsAdditionalPortsFromPortRequests() {
        // Given
        String username = "testuser";

        UsedId uid = mock(UsedId.class);
        when(uid.getIdValue()).thenReturn(10000L);

        ContainerImage image = mock(ContainerImage.class);
        when(image.getImageName()).thenReturn("containerssh-guest");
        when(image.getImageVersion()).thenReturn("ubuntu22.04");

        ResourceGroup rg = mock(ResourceGroup.class);
        when(rg.getDescription()).thenReturn("GPU Group");
        when(rg.getServerName()).thenReturn("FARM-01");

        Request request = mock(Request.class);
        when(request.getRequestId()).thenReturn(1L);
        when(request.getUbuntuUsername()).thenReturn(username);
        when(request.getUbuntuUid()).thenReturn(uid);
        when(request.getContainerImage()).thenReturn(image);
        when(request.getResourceGroup()).thenReturn(rg);
        when(request.getRequestGroups()).thenReturn(new LinkedHashSet<>());
        when(request.getVolumeSizeGiB()).thenReturn(20L);

        when(requestRepository.findByUbuntuUsername(username)).thenReturn(Optional.of(request));
        when(nodeRepository.findAllByResourceGroup(rg)).thenReturn(List.of());

        // additional ports: 사용자가 신청한 포트 (tensorboard, 포트 번호는 시스템 할당)
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

        UsedId uid = mock(UsedId.class);
        when(uid.getIdValue()).thenReturn(10001L);

        ContainerImage image = mock(ContainerImage.class);
        when(image.getImageName()).thenReturn("cuda");
        when(image.getImageVersion()).thenReturn("11.8");

        ResourceGroup rg = mock(ResourceGroup.class);
        when(rg.getDescription()).thenReturn("GPU Group");
        when(rg.getServerName()).thenReturn("FARM-02");

        Request request = mock(Request.class);
        when(request.getRequestId()).thenReturn(2L);
        when(request.getUbuntuUsername()).thenReturn(username);
        when(request.getUbuntuUid()).thenReturn(uid);
        when(request.getContainerImage()).thenReturn(image);
        when(request.getResourceGroup()).thenReturn(rg);
        when(request.getRequestGroups()).thenReturn(new LinkedHashSet<>());
        when(request.getVolumeSizeGiB()).thenReturn(10L);

        when(requestRepository.findByUbuntuUsername(username)).thenReturn(Optional.of(request));
        when(nodeRepository.findAllByResourceGroup(rg)).thenReturn(List.of());

        PortRequests portReq = mock(PortRequests.class);
        when(portReq.getInternalPort()).thenReturn(6006);
        when(portReq.getUsagePurpose()).thenReturn("tensorboard");
        // portNumber(외부포트)는 PortRequests에 있지만 AdditionalPortDTO에는 포함되지 않음

        when(portRequestRepository.findByRequestRequestId(2L))
                .thenReturn(List.of(portReq));

        // When
        AcceptInfoResponseDTO result = service.getAcceptInfo(username);

        // Then - AdditionalPortDTO는 internal_port, usage_purpose만 갖는다
        assertThat(result.additional_ports()).hasSize(1);
        AcceptInfoResponseDTO.AdditionalPortDTO portDTO = result.additional_ports().get(0);
        assertThat(portDTO.internal_port()).isEqualTo(6006);
        assertThat(portDTO.usage_purpose()).isEqualTo("tensorboard");
        // AdditionalPortDTO 구조상 external_port 필드 자체가 존재하지 않음을 타입으로 보장
    }

    @Test
    @DisplayName("additional_ports가 없으면 빈 리스트를 반환한다")
    void getAcceptInfo_noAdditionalPorts_returnsEmptyList() {
        // Given
        String username = "testuser3";

        UsedId uid = mock(UsedId.class);
        when(uid.getIdValue()).thenReturn(10002L);

        ContainerImage image = mock(ContainerImage.class);
        when(image.getImageName()).thenReturn("cuda");
        when(image.getImageVersion()).thenReturn("11.8");

        ResourceGroup rg = mock(ResourceGroup.class);
        when(rg.getDescription()).thenReturn("GPU Group");
        when(rg.getServerName()).thenReturn("FARM-03");

        Request request = mock(Request.class);
        when(request.getRequestId()).thenReturn(3L);
        when(request.getUbuntuUsername()).thenReturn(username);
        when(request.getUbuntuUid()).thenReturn(uid);
        when(request.getContainerImage()).thenReturn(image);
        when(request.getResourceGroup()).thenReturn(rg);
        when(request.getRequestGroups()).thenReturn(new LinkedHashSet<>());
        when(request.getVolumeSizeGiB()).thenReturn(10L);

        when(requestRepository.findByUbuntuUsername(username)).thenReturn(Optional.of(request));
        when(nodeRepository.findAllByResourceGroup(rg)).thenReturn(List.of());
        when(portRequestRepository.findByRequestRequestId(3L)).thenReturn(List.of());

        // When
        AcceptInfoResponseDTO result = service.getAcceptInfo(username);

        // Then
        assertThat(result.additional_ports()).isEmpty();
    }

    @Test
    @DisplayName("gpu_nodes가 Node 정보로부터 올바르게 변환된다")
    void getAcceptInfo_convertsNodeInfoToGpuNodes() {
        // Given
        String username = "testuser4";

        UsedId uid = mock(UsedId.class);
        when(uid.getIdValue()).thenReturn(10003L);

        ContainerImage image = mock(ContainerImage.class);
        when(image.getImageName()).thenReturn("cuda");
        when(image.getImageVersion()).thenReturn("11.8");

        ResourceGroup rg = mock(ResourceGroup.class);
        when(rg.getDescription()).thenReturn("GPU Group");
        when(rg.getServerName()).thenReturn("FARM-01");

        Request request = mock(Request.class);
        when(request.getRequestId()).thenReturn(4L);
        when(request.getUbuntuUsername()).thenReturn(username);
        when(request.getUbuntuUid()).thenReturn(uid);
        when(request.getContainerImage()).thenReturn(image);
        when(request.getResourceGroup()).thenReturn(rg);
        when(request.getRequestGroups()).thenReturn(new LinkedHashSet<>());
        when(request.getVolumeSizeGiB()).thenReturn(10L);

        Node node = mock(Node.class);
        when(node.getNodeId()).thenReturn("farm1");
        when(node.getCpuCoreCount()).thenReturn(4);
        when(node.getMemorySizeGB()).thenReturn(10);
        when(node.getNumberGpu()).thenReturn(2);

        when(requestRepository.findByUbuntuUsername(username)).thenReturn(Optional.of(request));
        when(nodeRepository.findAllByResourceGroup(rg)).thenReturn(List.of(node));
        when(portRequestRepository.findByRequestRequestId(4L)).thenReturn(List.of());

        // When
        AcceptInfoResponseDTO result = service.getAcceptInfo(username);

        // Then
        assertThat(result.gpu_nodes()).hasSize(1);
        AcceptInfoResponseDTO.NodeDTO nodeDTO = result.gpu_nodes().get(0);
        assertThat(nodeDTO.node_name()).isEqualTo("farm1");
        assertThat(nodeDTO.cpu_limit()).isEqualTo("4000m");
        assertThat(nodeDTO.memory_limit()).isEqualTo("10240Mi");
        assertThat(nodeDTO.num_gpu()).isEqualTo(2);
    }
}
