package DGU_AI_LAB.admin_be.domain.requests.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.time.LocalDateTime;

public record ModifyRequestDTO(
        @NotBlank(message = "변경 사유는 필수입니다.")
        String reason,

        @Positive(message = "볼륨 크기는 양수여야 합니다.")
        Long requestedVolumeSizeGiB,

        LocalDateTime requestedExpiresAt
) {
}