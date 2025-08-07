package DGU_AI_LAB.admin_be.domain.users.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ApprovalAuthRequestDTO(
        @NotBlank String username,
        @NotBlank String passwordBase64
) {}
