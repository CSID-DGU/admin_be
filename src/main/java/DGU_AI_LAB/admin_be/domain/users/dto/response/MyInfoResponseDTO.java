package DGU_AI_LAB.admin_be.domain.users.dto.response;

import DGU_AI_LAB.admin_be.domain.users.entity.Role;
import DGU_AI_LAB.admin_be.domain.users.entity.User;

import java.time.LocalDateTime;

public record MyInfoResponseDTO(
        Long userId,
        String email,
        String studentId,
        String name,
        String phone,
        String department,
        Boolean isActive,
        Role role,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static MyInfoResponseDTO fromEntity(User u) {
        return new MyInfoResponseDTO(
                u.getUserId(),
                u.getEmail(),
                u.getStudentId(),
                u.getName(),
                u.getPhone(),
                u.getDepartment(),
                u.getIsActive(),
                u.getRole(),
                u.getCreatedAt(),
                u.getUpdatedAt()
        );
    }
}
