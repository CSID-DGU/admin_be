package DGU_AI_LAB.admin_be.domain.requests.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "관리자용 변경 요청 거절 DTO")
public record RejectModificationDTO(

        @Schema(description = "거절할 변경 요청 ID", example = "1")
        @NotNull(message = "변경 요청 ID는 필수입니다.")
        Long changeRequestId,
        @Schema(description = "관리자 거절 코멘트", example = "변경 사유가 불충분하여 거절합니다.")
        @NotBlank(message = "거절 사유는 필수입니다.")
        String adminComment
) {
}