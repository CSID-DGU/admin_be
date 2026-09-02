package DGU_AI_LAB.admin_be.domain.requests.dto.response;

import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Schema(description = "리소스 사용량 응답 DTO")
@Builder
public record ResourceUsageDTO(
        @Schema(description = "사용자 고유 ID", example = "1")
        Long userId,
        @Schema(description = "사용자 이름", example = "이수아")
        String userName,
        @Schema(description = "리소스 그룹 ID", example = "1")
        Integer resourceGroupId
) {
    public static ResourceUsageDTO fromEntity(Request request) {
        ResourceGroup resourceGroup = request.getResourceGroup();

        return ResourceUsageDTO.builder()
                .userId(request.getUser().getUserId())
                .userName(request.getUser().getName())
                .resourceGroupId(resourceGroup != null ? resourceGroup.getRsgroupId() : null)
                .build();
    }
}
