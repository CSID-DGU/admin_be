package DGU_AI_LAB.admin_be.domain.users.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "사용자 비밀번호 변경 요청 DTO")
public record PasswordUpdateRequestDTO(
        @Schema(description = "현재 비밀번호", example = "1234")
        @NotBlank(message = "현재 비밀번호는 필수로 입력해야 합니다.")
        String currentPassword,
        @Schema(description = "새 비밀번호", example = "1234!")
        @NotBlank(message = "새 비밀번호는 필수로 입력해야 합니다.")
        String newPassword
) {}