package DGU_AI_LAB.admin_be.domain.resourceGroups.dto.response;

import DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Schema(description = "리소스 그룹 조회 응답 DTO")
@Builder
@Schema(description = "리소스 그룹 응답 DTO")
public record ResourceGroupResponseDTO(
        @Schema(description = "리소스 그룹 ID", example = "1")
        Integer rsgroupId,
        @Schema(description = "리소스 그룹 설명", example = "High-performance GPU cluster with RTX 4090 cards")
        String description,
        @Schema(description = "서버명", example = "LAB")
        String serverName
) {
    public static ResourceGroupResponseDTO fromEntity(ResourceGroup resourceGroup) {
        return ResourceGroupResponseDTO.builder()
                .rsgroupId(resourceGroup.getRsgroupId())
                .description(resourceGroup.getDescription())
                .serverName(resourceGroup.getServerName())
                .build();
    }
}
