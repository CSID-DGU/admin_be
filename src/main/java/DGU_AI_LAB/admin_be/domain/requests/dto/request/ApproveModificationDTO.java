package DGU_AI_LAB.admin_be.domain.requests.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ApproveModificationDTO(
        @NotNull(message = "변경 요청 ID는 필수입니다.")
        Long changeRequestId,
        @NotBlank(message = "승인 사유는 필수입니다.")
        String adminComment
) {
}