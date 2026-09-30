package DGU_AI_LAB.admin_be.domain.users.dto.request;

import DGU_AI_LAB.admin_be.global.validation.MaxUtf8Bytes;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "관리자 비밀번호 초기화 요청 DTO")
public record AdminPasswordResetRequestDTO(
        // 가입 화면도 8자 이상을 요구한다. BCrypt는 72바이트를 넘으면 거절하므로 그 이상은 받지 않는다.
        @Schema(description = "새 비밀번호. 관리자가 사용자에게 직접 전달한다(메일로 보내지 않는다)", example = "strongPassword123!")
        @NotBlank(message = "새 비밀번호를 입력해 주세요.")
        @Size(min = 8, max = 72, message = "새 비밀번호는 8~72자로 정해 주세요.")
        @MaxUtf8Bytes(value = 72, message = "새 비밀번호가 너무 깁니다. 한글은 한 글자가 3바이트라 24자까지 쓸 수 있습니다.")
        String newPassword
) {}
