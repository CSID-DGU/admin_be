package DGU_AI_LAB.admin_be.domain.users.dto.response;

public record UserTokenResponseDTO(
        String accessToken,
        String refreshToken
) {
    public static UserTokenResponseDTO of(String accessToken, String refreshToken) {
        return new UserTokenResponseDTO(accessToken, refreshToken);
    }
}
