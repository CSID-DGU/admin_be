package DGU_AI_LAB.admin_be.domain.users.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "사용자 비밀번호 변경 요청 DTO")
public record PasswordUpdateRequestDTO(
        @Schema(description = "현재 비밀번호", example = "1234")
        @NotBlank(message = "현재 비밀번호는 필수로 입력해야 합니다.")
        String currentPassword,

        // 화면(계정 설정)도 8자 이상을 요구한다. BCrypt는 72바이트 뒤를 버리므로 그 이상은 받지 않는다.
        @Schema(description = "새 비밀번호", example = "strongPassword123!")
        @NotBlank(message = "새 비밀번호는 필수로 입력해야 합니다.")
        @Size(min = 8, max = 72, message = "새 비밀번호는 8~72자여야 합니다.")
        String newPassword
) {}
