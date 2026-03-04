package DGU_AI_LAB.admin_be.domain.users.dto.response;

import DGU_AI_LAB.admin_be.domain.users.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "사용자 기본 정보 응답 DTO")
public record UserResponseDTO(
        @Schema(description = "사용자를 고유하게 식별하는 번호") Long userId,
        @Schema(description = "사용자의 실명") String username,
        @Schema(description = "계정 활성화 여부 (true: 활성, false: 비활성)") Boolean isActive
) {
    public static UserResponseDTO fromEntity(User user) {
        return new UserResponseDTO(
                user.getUserId(),
                user.getName(),
                user.getIsActive()
        );
    }
}
