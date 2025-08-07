package DGU_AI_LAB.admin_be.domain.users.dto.response;

public record UserAuthResponseDTO(
        Boolean success,
        String authenticatedUsername
) {
}
