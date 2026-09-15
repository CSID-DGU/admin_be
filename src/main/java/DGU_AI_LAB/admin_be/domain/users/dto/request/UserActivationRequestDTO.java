package DGU_AI_LAB.admin_be.domain.users.dto.request;

import jakarta.validation.constraints.NotNull;

/** 사용자 활성 상태 변경 본문. */
public record UserActivationRequestDTO(
        @NotNull(message = "active 값은 필수입니다.")
        Boolean active
) {}
