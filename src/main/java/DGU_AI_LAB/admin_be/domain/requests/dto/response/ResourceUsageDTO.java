package DGU_AI_LAB.admin_be.domain.requests.dto.response;

import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import lombok.Builder;

@Builder
public record ResourceUsageDTO(
        Long userId,
        String userName,
        Integer resourceGroupId,
        Long volumeSizeByte
) {
    public static ResourceUsageDTO fromEntity(Request request) {
        return ResourceUsageDTO.builder()
                .userId(request.getUser().getUserId())
                .userName(request.getUser().getName())
                .resourceGroupId(request.getResourceGroup().getRsgroupId())
                .volumeSizeByte(request.getVolumeSizeByte())
                .build();
    }
}
