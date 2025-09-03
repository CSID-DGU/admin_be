package DGU_AI_LAB.admin_be.domain.users.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "사용자 연락처 변경 요청 DTO")
public record PhoneUpdateRequestDTO(
        @Schema(description = "새 연락처", example = "010-1234-5678")
        @NotBlank(message = "연락처는 필수로 입력해야 합니다.")
        @Pattern(regexp = "^\\d{2,3}-\\d{3,4}-\\d{4}$", message = "유효한 전화번호 형식이 아닙니다. (예: 010-1234-5678)")
        String newPhone
) {}