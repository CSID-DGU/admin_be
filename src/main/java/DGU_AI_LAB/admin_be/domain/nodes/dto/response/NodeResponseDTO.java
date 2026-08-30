package DGU_AI_LAB.admin_be.domain.nodes.dto.response;

import DGU_AI_LAB.admin_be.domain.nodes.entity.Node;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Schema(description = "노드 응답 DTO")
@Builder
public record NodeResponseDTO(
        @Schema(description = "노드 ID", example = "farm1")
        String nodeId,
        @Schema(description = "소속 리소스 그룹명", example = "3090ti")
        String resourceGroupName,
        @Schema(description = "메모리 크기(GB)", example = "128")
        Integer memorySizeGB,
        @Schema(description = "CPU 코어 수", example = "32")
        Integer cpuCoreCount,
        @Schema(description = "GPU 개수", example = "4")
        Integer numberGpu
) {
    public static NodeResponseDTO fromEntity(Node node) {
        return NodeResponseDTO.builder()
                .nodeId(node.getNodeId())
                .resourceGroupName(node.getResourceGroup().getResourceGroupName())
                .memorySizeGB(node.getMemorySizeGB())
                .cpuCoreCount(node.getCpuCoreCount())
                .numberGpu(node.getNumberGpu())
                .build();
    }
}
