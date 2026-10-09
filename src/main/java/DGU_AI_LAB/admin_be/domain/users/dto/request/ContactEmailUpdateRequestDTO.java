package DGU_AI_LAB.admin_be.domain.users.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

@Schema(description = "자주 사용하는 이메일 변경 요청 DTO")
public record ContactEmailUpdateRequestDTO(
        @Schema(description = "자주 사용하는 이메일 (비우거나 학교 이메일과 같게 적으면 지운다)", example = "user@gmail.com")
        @Email @Size(max = 100)
        String contactEmail
) {}
