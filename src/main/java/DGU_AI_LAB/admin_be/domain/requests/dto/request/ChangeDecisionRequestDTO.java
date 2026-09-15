package DGU_AI_LAB.admin_be.domain.requests.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 변경 요청 승인·거절 본문. 대상 변경 요청은 경로의 번호로 정한다. */
public record ChangeDecisionRequestDTO(
        // change_request.admin_comment가 500자다.
        @NotBlank(message = "사유는 필수입니다.")
        @Size(max = 500, message = "사유는 500자 이하여야 합니다.")
        String adminComment
) {}
