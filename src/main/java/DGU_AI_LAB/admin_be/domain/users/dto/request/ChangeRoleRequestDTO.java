package DGU_AI_LAB.admin_be.domain.users.dto.request;

import DGU_AI_LAB.admin_be.domain.users.entity.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "사용자 권한 변경 요청 DTO")
public record ChangeRoleRequestDTO(
        @Schema(description = "변경할 권한", example = "ADMIN")
        @NotNull
        Role role
) {
}
