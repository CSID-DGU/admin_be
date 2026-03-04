package DGU_AI_LAB.admin_be.domain.resourceGroups.dto.response;

import DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Schema(description = "리소스 그룹 조회 응답 DTO")
@Builder
public record ResourceGroupResponseDTO(
        @Schema(description = "리소스 그룹 ID") Integer rsgroupId,
        @Schema(description = "리소스 그룹 설명") String description,
        @Schema(description = "서버 이름") String serverName
) {
    public static ResourceGroupResponseDTO fromEntity(ResourceGroup resourceGroup) {
        return ResourceGroupResponseDTO.builder()
                .rsgroupId(resourceGroup.getRsgroupId())
                .description(resourceGroup.getDescription())
                .serverName(resourceGroup.getServerName())
                .build();
    }
}
