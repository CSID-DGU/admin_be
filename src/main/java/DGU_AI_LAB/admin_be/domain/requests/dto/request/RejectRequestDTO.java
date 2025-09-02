package DGU_AI_LAB.admin_be.domain.requests.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "1. 관리자용 요청 거절 요청 DTO")
public record RejectRequestDTO(

        @Schema(description = "거절할 요청 ID", example = "3")
        @NotNull(message = "요청 ID는 필수로 입력해야 합니다.")
        Long requestId,

        @Schema(description = "관리자 거절 코멘트", example = "신청서 양식에 맞지 않아 거절합니다.")
        @NotBlank(message = "거절 사유를 필수로 입력해야 합니다.")
        String adminComment
) {}
