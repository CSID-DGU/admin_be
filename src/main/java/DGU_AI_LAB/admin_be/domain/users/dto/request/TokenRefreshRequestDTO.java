package DGU_AI_LAB.admin_be.domain.users.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "토큰 갱신 요청 DTO")
public record TokenRefreshRequestDTO(
        @Schema(description = "로그인 또는 직전 갱신에서 받은 리프레시 토큰")
        @NotBlank
        String refreshToken
) {
}
