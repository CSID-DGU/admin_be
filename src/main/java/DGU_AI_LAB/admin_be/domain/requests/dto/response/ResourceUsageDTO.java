package DGU_AI_LAB.admin_be.domain.requests.dto.response;

import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Schema(description = "리소스 사용량 조회 응답 DTO")
@Builder
public record ResourceUsageDTO(
        @Schema(description = "사용자 ID") Long userId,
        @Schema(description = "사용자 이름") String userName,
        @Schema(description = "리소스 그룹 ID") Integer resourceGroupId,
        @Schema(description = "볼륨 사용량 (GiB)") Long volumeSizeByte
) {
    public static ResourceUsageDTO fromEntity(Request request) {
        return ResourceUsageDTO.builder()
                .userId(request.getUser().getUserId())
                .userName(request.getUser().getName())
                .resourceGroupId(request.getResourceGroup().getRsgroupId())
                .volumeSizeByte(request.getVolumeSizeGiB())
                .build();
    }
}
