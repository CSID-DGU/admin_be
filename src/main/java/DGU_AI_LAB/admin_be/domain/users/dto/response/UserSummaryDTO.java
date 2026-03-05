package DGU_AI_LAB.admin_be.domain.users.dto.response;

import DGU_AI_LAB.admin_be.domain.users.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDateTime;

@Schema(description = "사용자 목록 조회 응답 DTO")
@Builder
@Schema(description = "사용자 요약 응답 DTO")
public record UserSummaryDTO(
        @Schema(description = "사용자 고유 ID", example = "1")
        Long userId,
        @Schema(description = "이름", example = "이수아")
        String name,
        @Schema(description = "이메일 주소", example = "yukyum6@gmail.com")
        String email,
        @Schema(description = "사용자 권한", example = "USER")
        String role,
        @Schema(description = "학번", example = "202312345")
        String studentId,
        @Schema(description = "전화번호", example = "010-1234-5678")
        String phone,
        @Schema(description = "학과", example = "컴퓨터공학과")
        String department,
        @Schema(description = "계정 활성화 여부", example = "true")
        Boolean isActive,
        @Schema(description = "계정 생성 일시", example = "2026-01-09T15:25:28")
        LocalDateTime createdAt,
        @Schema(description = "계정 수정 일시", example = "2026-01-09T15:25:28")
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
