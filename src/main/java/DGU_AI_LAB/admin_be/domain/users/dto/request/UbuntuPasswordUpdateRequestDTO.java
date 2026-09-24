package DGU_AI_LAB.admin_be.domain.users.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Ubuntu 비밀번호 변경 요청 DTO")
public record UbuntuPasswordUpdateRequestDTO(
        @Schema(description = "현재 웹 로그인 비밀번호(본인 확인용)", example = "1234")
        @NotBlank(message = "현재 비밀번호는 필수로 입력해야 합니다.")
        String currentPassword,

        // 신청 화면과 같은 규칙이다(SaveRequestRequestDTO.ubuntuPassword).
        @Schema(description = "새 Ubuntu 비밀번호", example = "strongPassword123!")
        @NotBlank(message = "새 Ubuntu 비밀번호는 필수로 입력해야 합니다.")
        @Size(min = 8, max = 128, message = "새 Ubuntu 비밀번호는 8~128자여야 합니다.")
        String newPassword
) {}
