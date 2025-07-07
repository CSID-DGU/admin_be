package DGU_AI_LAB.admin_be.domain.user.dto.request;

import jakarta.validation.constraints.NotBlank;

public record UserTokenRequestDTO(
        @NotBlank
        String accessToken,
        @NotBlank
        String refreshToken
) {
}
