package DGU_AI_LAB.admin_be.domain.requests.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관리자용 변경 요청 거절 DTO")
public record RejectModificationDTO(

        @NotNull(message = "변경 요청 ID는 필수입니다.")
        Long changeRequestId,
        @NotBlank(message = "거절 사유는 필수입니다.")
        String adminComment
) {
}