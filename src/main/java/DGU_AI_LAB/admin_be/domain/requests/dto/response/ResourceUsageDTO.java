package DGU_AI_LAB.admin_be.domain.requests.dto.response;

import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Schema(description = "리소스 사용량 조회 응답 DTO")
@Builder
@Schema(description = "리소스 사용량 응답 DTO")
public record ResourceUsageDTO(
        @Schema(description = "사용자 고유 ID", example = "1")
        Long userId,
        @Schema(description = "사용자 이름", example = "이수아")
        String userName,
        @Schema(description = "리소스 그룹 ID", example = "1")
        Integer resourceGroupId,
        @Schema(description = "볼륨 크기 (GiB)", example = "20")
        Long volumeSizeByte
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
