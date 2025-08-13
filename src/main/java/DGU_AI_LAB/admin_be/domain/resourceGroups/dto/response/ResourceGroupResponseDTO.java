package DGU_AI_LAB.admin_be.domain.resourceGroups.dto.response;

import DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup;
import lombok.Builder;

@Builder
public record ResourceGroupResponseDTO(
        Integer rsgroupId,
        String description
) {
    public static ResourceGroupResponseDTO fromEntity(ResourceGroup resourceGroup) {
        return ResourceGroupResponseDTO.builder()
                .rsgroupId(resourceGroup.getRsgroupId())
                .description(resourceGroup.getDescription())
                .build();
    }
}