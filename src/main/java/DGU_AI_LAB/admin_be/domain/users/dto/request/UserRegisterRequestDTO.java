package DGU_AI_LAB.admin_be.domain.users.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record UserRegisterRequestDTO(
        @Email @NotBlank String email,
        @NotBlank String password,
        @NotBlank String name
) {}
