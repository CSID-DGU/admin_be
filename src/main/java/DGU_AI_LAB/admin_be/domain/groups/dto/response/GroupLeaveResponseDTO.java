package DGU_AI_LAB.admin_be.domain.groups.dto.response;

import DGU_AI_LAB.admin_be.domain.groups.entity.GroupLeaveRequest;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "그룹 탈퇴 신청")
public record GroupLeaveResponseDTO(
        @Schema(description = "탈퇴 신청 번호", example = "3")
        Long leaveRequestId,
        @Schema(description = "PENDING(승인 대기) / APPROVED(승인 — 제거 작업 등록. 작업 결과는 그룹 작업 기록에서 본다) / REJECTED(거절)", example = "PENDING")
        String status,
        @Schema(description = "신청한 사용자 번호", example = "7")
        Long userId,
        @Schema(description = "신청한 사용자 이름", example = "홍길동")
        String userName,
        @Schema(description = "나가려는 그룹 번호", example = "12")
        Long groupId,
        @Schema(description = "나가려는 그룹 이름", example = "vision-lab")
        String groupName,
        @Schema(description = "신청 시각")
        LocalDateTime createdAt,
        @Schema(description = "승인·거절 시각. 대기 중이면 null")
        LocalDateTime decidedAt
) {
    public static GroupLeaveResponseDTO fromEntity(GroupLeaveRequest request) {
        return new GroupLeaveResponseDTO(
                request.getGroupLeaveRequestId(),
                request.getStatus().name(),
                request.getUser().getUserId(),
                request.getUser().getName(),
                request.getGroup().getGroupId(),
                request.getGroup().getGroupName(),
                request.getCreatedAt(),
                request.getDecidedAt());
    }
}
