package DGU_AI_LAB.admin_be.domain.users.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "토큰 응답 DTO")
public record UserTokenResponseDTO(
        @Schema(description = "액세스 토큰 (JWT)", example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ5dWt5dW02QGdtYWlsLmNvbSJ9.abc123")
        String accessToken,
        @Schema(description = "리프레시 토큰 (JWT)", example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ5dWt5dW02QGdtYWlsLmNvbSJ9.xyz456")
        String refreshToken
) {
    public static UserTokenResponseDTO of(String accessToken, String refreshToken) {
        return new UserTokenResponseDTO(accessToken, refreshToken);
    }
}
