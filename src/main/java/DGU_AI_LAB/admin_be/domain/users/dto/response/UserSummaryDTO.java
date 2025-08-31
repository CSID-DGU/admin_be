package DGU_AI_LAB.admin_be.domain.users.dto.response;

import DGU_AI_LAB.admin_be.domain.users.entity.User;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record UserSummaryDTO(
        Long userId,
        String name,
        String email,
        String role,
        String studentId,
        String phone,
        String department,
        Boolean isActive,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static UserSummaryDTO fromEntity(User user) {
        return UserSummaryDTO.builder()
                .userId(user.getUserId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole().name())
                .studentId(user.getStudentId())
                .phone(user.getPhone())
                .department(user.getDepartment())
                .isActive(user.getIsActive())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
