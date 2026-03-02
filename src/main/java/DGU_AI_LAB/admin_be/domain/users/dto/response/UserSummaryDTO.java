package DGU_AI_LAB.admin_be.domain.users.dto.response;

import DGU_AI_LAB.admin_be.domain.users.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDateTime;

@Schema(description = "사용자 목록 조회 응답 DTO")
@Builder
public record UserSummaryDTO(
        @Schema(description = "사용자를 고유하게 식별하는 번호") Long userId,
        @Schema(description = "사용자의 실명") String name,
        @Schema(description = "로그인 및 알림에 사용되는 이메일 주소") String email,
        @Schema(description = "시스템 권한 (ADMIN: 관리자, USER: 일반 사용자)") String role,
        @Schema(description = "동국대학교 학번") String studentId,
        @Schema(description = "연락 가능한 전화번호") String phone,
        @Schema(description = "소속 학과명") String department,
        @Schema(description = "계정 활성화 여부 (true: 활성, false: 비활성)") Boolean isActive,
        @Schema(description = "계정이 처음 생성된 날짜 및 시간") LocalDateTime createdAt,
        @Schema(description = "계정 정보가 마지막으로 수정된 날짜 및 시간") LocalDateTime updatedAt
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
