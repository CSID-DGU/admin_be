package DGU_AI_LAB.admin_be.domain.requests.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

@Schema(description = "관리자용 요청 승인 요청 DTO")
public record ApproveRequestDTO(

        @Schema(description = "승인할 요청 ID", example = "1")
        @NotNull(message = "요청 ID는 필수로 입력해야 합니다.")
        Long requestId,

        @Schema(description = "적용할 컨테이너 이미지 ID", example = "1")
        @NotNull(message = "이미지 ID는 필수로 입력해야 합니다.")
        Long imageId,

        @Schema(description = "할당할 리소스 그룹 ID", example = "1")
        @NotNull(message = "리소스 그룹 ID는 필수로 입력해야 합니다.")
        Integer resourceGroupId,

        @Schema(description = "할당할 볼륨 크기 (GiB)", example = "20")
        @NotNull(message = "볼륨 크기는 필수로 입력해야 합니다.")
        Long volumeSizeGiB,

        @Schema(description = "만료일", example = "2025-12-31T23:59:59")
        @NotNull(message = "만료일은 필수로 입력해야 합니다.")
        LocalDateTime expiresAt,

        @Schema(description = "관리자 승인 코멘트 (선택 사항)", example = "사용 목적에 따라 리소스를 할당함")
        String adminComment
) {}
