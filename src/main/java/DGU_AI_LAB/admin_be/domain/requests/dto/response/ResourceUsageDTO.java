package DGU_AI_LAB.admin_be.domain.requests.dto.response;

import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import lombok.Builder;

@Builder
public record ResourceUsageDTO(
        Long userId,
        String userName,
        String nodeId,
        Integer memorySizeGB,
        Integer cpuCoreCount,
        Long volumeSizeByte
) {
    public static ResourceUsageDTO fromEntity(Request request) {
        return ResourceUsageDTO.builder()
                .userId(request.getUser().getUserId())
                .userName(request.getUser().getName())
                .nodeId(request.getNode().getNodeId())
                .memorySizeGB(request.getNode().getMemorySizeGB())
                .cpuCoreCount(request.getNode().getCpuCoreCount())
                .volumeSizeByte(request.getVolumeSizeByte())
                .build();
    }
}
