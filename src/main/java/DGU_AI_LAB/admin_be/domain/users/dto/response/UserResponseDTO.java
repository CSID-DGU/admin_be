package DGU_AI_LAB.admin_be.domain.users.dto.response;

import DGU_AI_LAB.admin_be.domain.users.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "사용자 응답 DTO")
public record UserResponseDTO(
        @Schema(description = "사용자 고유 ID", example = "1")
        Long userId,
        @Schema(description = "사용자 이름", example = "이수아")
        String username,
        @Schema(description = "계정 활성화 여부", example = "true")
        Boolean isActive
) {
    public static UserResponseDTO fromEntity(User user) {
        return new UserResponseDTO(
                user.getUserId(),
                user.getName(),
                user.getIsActive()
        );
    }
}
