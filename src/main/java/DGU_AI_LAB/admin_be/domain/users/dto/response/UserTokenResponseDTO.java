package DGU_AI_LAB.admin_be.domain.users.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "로그인 토큰 응답 DTO")
public record UserTokenResponseDTO(
        @Schema(description = "API 요청 시 Authorization 헤더에 사용하는 JWT 액세스 토큰") String accessToken,
        @Schema(description = "액세스 토큰 만료 시 재발급에 사용하는 JWT 리프레시 토큰") String refreshToken
) {
    public static UserTokenResponseDTO of(String accessToken, String refreshToken) {
        return new UserTokenResponseDTO(accessToken, refreshToken);
    }
}
