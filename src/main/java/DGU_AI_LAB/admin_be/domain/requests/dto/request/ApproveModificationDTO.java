package DGU_AI_LAB.admin_be.domain.requests.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "관리자용 변경 요청 승인 DTO")
public record ApproveModificationDTO(

        @Schema(description = "승인할 변경 요청 ID", example = "5")
        @NotNull(message = "변경 요청 ID는 필수입니다.")
        Long changeRequestId,

        @Schema(description = "관리자 승인 코멘트", example = "변경 요청을 승인합니다.")
        @NotBlank(message = "승인 사유는 필수입니다.")
        String adminComment
) {}