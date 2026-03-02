package DGU_AI_LAB.admin_be.domain.requests.dto.response;

import DGU_AI_LAB.admin_be.domain.requests.entity.ChangeRequest;
import DGU_AI_LAB.admin_be.domain.requests.entity.ChangeType;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import com.fasterxml.jackson.annotation.JsonRawValue;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDateTime;

@Schema(description = "변경 요청 조회 응답 DTO")
@Builder
public record ChangeRequestResponseDTO(
        @Schema(description = "변경 요청 ID") Long changeRequestId,
        @Schema(description = "원본 신청 ID") Long originalRequestId,
        @Schema(description = "변경 유형 (EXTENSION: 기간 연장, PORT: 포트 변경 등)") ChangeType changeType,
        @Schema(description = "변경 전 값 (JSON)") @JsonRawValue String oldValue,
        @Schema(description = "변경 후 값 (JSON)") @JsonRawValue String newValue,
        @Schema(description = "변경 사유") String reason,
        @Schema(description = "요청 상태 (PENDING: 대기, FULFILLED: 승인, REJECTED: 거절)") Status status,
        @Schema(description = "관리자 코멘트") String adminComment,
        @Schema(description = "요청자 정보") AdminUserInfo requestedBy,
        @Schema(description = "요청 생성 일시") LocalDateTime createdAt
) {
    public static ChangeRequestResponseDTO fromEntity(ChangeRequest changeRequest) {
        return ChangeRequestResponseDTO.builder()
                .changeRequestId(changeRequest.getChangeRequestId())
                .originalRequestId(changeRequest.getRequest().getRequestId())
                .changeType(changeRequest.getChangeType())
                .oldValue(changeRequest.getOldValue())
                .newValue(changeRequest.getNewValue())
                .reason(changeRequest.getReason())
                .status(changeRequest.getStatus())
                .adminComment(changeRequest.getAdminComment())
                .requestedBy(AdminUserInfo.fromEntity(changeRequest.getRequestedBy()))
                .createdAt(changeRequest.getCreatedAt())
                .build();
    }

    @Schema(description = "요청자 요약 정보")
    @Builder
    public record AdminUserInfo(
            @Schema(description = "사용자 ID") Long userId,
            @Schema(description = "이메일") String email,
            @Schema(description = "실명") String name
    ) {
        public static AdminUserInfo fromEntity(User user) {
            return AdminUserInfo.builder()
                    .userId(user.getUserId())
                    .email(user.getEmail())
                    .name(user.getName())
                    .build();
        }
    }
}