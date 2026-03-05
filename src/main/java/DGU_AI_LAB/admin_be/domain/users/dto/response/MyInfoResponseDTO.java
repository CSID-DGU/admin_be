package DGU_AI_LAB.admin_be.domain.users.dto.response;

import DGU_AI_LAB.admin_be.domain.users.entity.Role;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "내 정보 응답 DTO")
public record MyInfoResponseDTO(
        @Schema(description = "사용자 고유 ID", example = "1")
        Long userId,
        @Schema(description = "이메일 주소", example = "yukyum6@gmail.com")
        String email,
        @Schema(description = "학번", example = "202312345")
        String studentId,
        @Schema(description = "이름", example = "이수아")
        String name,
        @Schema(description = "전화번호", example = "010-1234-5678")
        String phone,
        @Schema(description = "학과", example = "컴퓨터공학과")
        String department,
        @Schema(description = "계정 활성화 여부", example = "true")
        Boolean isActive,
        @Schema(description = "사용자 권한", example = "USER", allowableValues = {"USER", "ADMIN"})
        Role role,
        @Schema(description = "계정 생성 일시", example = "2026-01-09T15:25:28")
        LocalDateTime createdAt,
        @Schema(description = "계정 수정 일시", example = "2026-01-09T15:25:28")
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
