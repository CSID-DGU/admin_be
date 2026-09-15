package DGU_AI_LAB.admin_be.domain.requests.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 거절 본문. 대상 신청은 경로의 번호로 정한다. */
public record RejectionRequestDTO(
        @NotBlank(message = "거절 사유를 필수로 입력해야 합니다.")
        @Size(max = 300, message = "거절 사유는 300자 이하여야 합니다.")
        String adminComment
) {}
