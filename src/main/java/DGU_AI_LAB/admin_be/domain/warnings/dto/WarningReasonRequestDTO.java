package DGU_AI_LAB.admin_be.domain.warnings.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "경고 부여·차감·취소 요청")
public record WarningReasonRequestDTO(
        @Schema(description = "사유", example = "오류 Q&A에 있는 내용을 확인하지 않고 신고")
        @NotBlank @Size(max = 500) String reason
) {}
