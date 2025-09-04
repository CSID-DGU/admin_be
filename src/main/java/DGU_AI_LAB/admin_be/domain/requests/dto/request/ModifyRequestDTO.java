package DGU_AI_LAB.admin_be.domain.requests.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.time.LocalDateTime;
import java.util.Set;

@Schema(description = "사용자용 서버 설정 변경 요청 DTO")
public record ModifyRequestDTO(

        @Schema(description = "변경 요청 사유 (필수)", example = "프로젝트 요구사항 변경으로 인한 용량 증설")
        @NotBlank(message = "변경 사유는 필수입니다.")
        String reason,

        @Schema(description = "요청하는 새로운 저장 공간 크기(GiB)", example = "200")
        @Positive(message = "볼륨 크기는 양수여야 합니다.")
        Long requestedVolumeSizeGiB,

        @Schema(description = "요청하는 새로운 만료 기한", example = "2026-09-02T10:00:00")
        LocalDateTime requestedExpiresAt,

        @Schema(description = "요청하는 새로운 그룹 ID 목록", example = "[1001, 1002]")
        Set<Long> requestedGroupIds,

        @Schema(description = "요청하는 새로운 리소스 그룹 ID", example = "2")
        Integer requestedResourceGroupId,

        @Schema(description = "요청하는 새로운 도커 이미지 ID", example = "3")
        Long requestedContainerImageId
) {}