package DGU_AI_LAB.admin_be.domain.users.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UserUpdateRequestDTO(
        @NotBlank String password,
        @NotNull Boolean isActive
) {}
