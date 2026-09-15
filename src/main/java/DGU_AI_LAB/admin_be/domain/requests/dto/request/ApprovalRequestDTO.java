package DGU_AI_LAB.admin_be.domain.requests.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 승인 본문. 대상 신청은 경로의 번호로 정한다. */
public record ApprovalRequestDTO(
        @NotNull(message = "이미지 ID는 필수로 입력해야 합니다.")
        Long imageId,
        @NotNull(message = "리소스 그룹 ID는 필수로 입력해야 합니다.")
        Integer resourceGroupId,
        // requests.admin_comment가 300자다.
        @Size(max = 300, message = "승인 코멘트는 300자 이하여야 합니다.")
        String adminComment
) {}
