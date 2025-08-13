package DGU_AI_LAB.admin_be.domain.users.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordUpdateRequestDTO(
        @NotBlank(message = "현재 비밀번호는 필수로 입력해야 합니다.")
        String currentPassword,
        @NotBlank(message = "새 비밀번호는 필수로 입력해야 합니다.")
        // @Size(min = 8, message = "새 비밀번호는 최소 8자 이상이어야 합니다.")
        String newPassword
) {}