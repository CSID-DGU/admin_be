package DGU_AI_LAB.admin_be.domain.gpus.dto.response;

import lombok.Builder;

@Builder
public record GpuTypeResponseDTO(
        String gpuModel,
        Integer ramGb,
        String resourceGroupName,
        Long availableNodes,
        Boolean isAvailable // TODO: 현재는 항상 true로 가정, 이후에 논의 후 수정 필요
) {}