package DGU_AI_LAB.admin_be.domain.users.dto.response;

import DGU_AI_LAB.admin_be.domain.users.entity.PasswordResetRequest;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "비밀번호 변경 요청 요약")
public record PasswordResetSummaryDTO(
        @Schema(description = "변경 요청 번호", example = "12")
        Long changeRequestId,
        @Schema(description = "신청자 ID", example = "5")
        Long userId,
        @Schema(description = "신청자 이름", example = "홍길동")
        String name,
        @Schema(description = "신청자 이메일", example = "test@dgu.ac.kr")
        String email,
        @Schema(description = "신청자의 우분투 계정명", example = "honggildong")
        String ubuntuUsername,
        @Schema(description = "상태(PENDING 승인 대기·PROCESSING 컨테이너 반영 중·FULFILLED 적용됨·DENIED 거절됨)", example = "PENDING")
        String status,
        @Schema(description = "신청 일시", example = "2026-10-01T15:25:28")
        LocalDateTime createdAt,
        @Schema(description = "마지막으로 바뀐 일시", example = "2026-10-01T15:25:28")
        LocalDateTime updatedAt
) {
    public static PasswordResetSummaryDTO fromEntity(PasswordResetRequest reset) {
        User user = reset.getUser();
        return new PasswordResetSummaryDTO(
                reset.getChangeRequest().getChangeRequestId(),
                user.getUserId(),
                user.getName(),
                user.getEmail(),
                user.getUbuntuUsername(),
                reset.getStatus().name(),
                reset.getCreatedAt(),
                reset.getUpdatedAt());
    }
}
