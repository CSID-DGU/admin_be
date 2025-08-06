package DGU_AI_LAB.admin_be.domain.users.dto.request;

import DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UserCreateRequestDTO(
        @NotBlank String name,
        @NotBlank String user_id,
        @NotBlank String email,
        @NotBlank String password,
        @NotNull Boolean isActive
) {
    public User toEntity() {
        return User.builder()
                .name(name)
                //.webId(user_id)
                .email(email)
                .password(password)
                .isActive(isActive)
                .build();
    }
}
