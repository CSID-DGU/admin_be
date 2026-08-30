package DGU_AI_LAB.admin_be.domain.nodes.service;

import DGU_AI_LAB.admin_be.domain.nodes.dto.response.NodeResponseDTO;
import DGU_AI_LAB.admin_be.domain.nodes.entity.Node;
import DGU_AI_LAB.admin_be.domain.nodes.repository.NodeRepository;
import DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NodeQueryServiceTest {

    @InjectMocks
    private NodeQueryService nodeQueryService;

    @Mock
    private NodeRepository nodeRepository;

    @Test
    @DisplayName("전체 노드를 NodeResponseDTO로 변환해 반환한다")
    void getAllNodes_returnsMappedList() {
        ResourceGroup rg = ResourceGroup.builder()
                .resourceGroupName("3090ti")
                .description("desc")
                .serverName("farm1")
                .build();
        Node node = Node.builder()
                .nodeId("farm1")
                .resourceGroup(rg)
                .memorySizeGB(128)
                .cpuCoreCount(32)
                .build();

        when(nodeRepository.findAll()).thenReturn(List.of(node));

        List<NodeResponseDTO> result = nodeQueryService.getAllNodes();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).nodeId()).isEqualTo("farm1");
        assertThat(result.get(0).resourceGroupName()).isEqualTo("3090ti");
        assertThat(result.get(0).numberGpu()).isZero();
    }

    @Test
    @DisplayName("노드가 없으면 빈 리스트를 반환한다")
    void getAllNodes_returnsEmptyList_whenNoNodes() {
        when(nodeRepository.findAll()).thenReturn(List.of());

        List<NodeResponseDTO> result = nodeQueryService.getAllNodes();

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("여러 노드가 있으면 전부 매핑해 반환한다")
    void getAllNodes_returnsAllMappedNodes_whenMultipleNodes() {
        ResourceGroup rg1 = ResourceGroup.builder()
                .resourceGroupName("3090ti").description("desc").serverName("farm1").build();
        ResourceGroup rg2 = ResourceGroup.builder()
                .resourceGroupName("a100").description("desc").serverName("farm2").build();
        Node node1 = Node.builder().nodeId("farm1").resourceGroup(rg1).memorySizeGB(128).cpuCoreCount(32).build();
        Node node2 = Node.builder().nodeId("farm2").resourceGroup(rg2).memorySizeGB(256).cpuCoreCount(64).build();

        when(nodeRepository.findAll()).thenReturn(List.of(node1, node2));

        List<NodeResponseDTO> result = nodeQueryService.getAllNodes();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(NodeResponseDTO::nodeId).containsExactly("farm1", "farm2");
    }
}
