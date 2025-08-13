package DGU_AI_LAB.admin_be.domain.users.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PhoneUpdateRequestDTO(
        @NotBlank(message = "연락처는 필수로 입력해야 합니다.")
        @Pattern(regexp = "^\\d{2,3}-\\d{3,4}-\\d{4}$", message = "유효한 전화번호 형식이 아닙니다. (예: 010-1234-5678)")
        String newPhone
) {}