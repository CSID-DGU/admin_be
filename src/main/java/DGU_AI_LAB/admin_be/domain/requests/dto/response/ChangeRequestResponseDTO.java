package DGU_AI_LAB.admin_be.domain.requests.dto.response;

import DGU_AI_LAB.admin_be.domain.requests.entity.ChangeRequest;
import DGU_AI_LAB.admin_be.domain.requests.entity.ChangeType;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import com.fasterxml.jackson.annotation.JsonRawValue;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
@Schema(description = "변경 요청 응답 DTO")
public record ChangeRequestResponseDTO(
        @Schema(description = "변경 요청 고유 ID", example = "1")
        Long changeRequestId,
        @Schema(description = "원본 서버 신청 ID", example = "42")
        Long originalRequestId,
        @Schema(description = "변경 타입", example = "VOLUME_SIZE", allowableValues = {"VOLUME_SIZE", "EXPIRES_AT", "GROUP", "RESOURCE_GROUP", "CONTAINER_IMAGE", "PORT"})
        ChangeType changeType,
        @Schema(description = "변경 전 값 (JSON)", example = "20")
        @JsonRawValue String oldValue,
        @Schema(description = "변경 후 값 (JSON)", example = "50")
        @JsonRawValue String newValue,
        @Schema(description = "변경 요청 사유", example = "프로젝트 요구사항 변경으로 인한 용량 증설")
        String reason,
        @Schema(description = "처리 상태", example = "PENDING", allowableValues = {"PENDING", "FULFILLED", "DENIED", "MODIFICATION_REQUESTED", "MODIFICATION_APPROVED", "MODIFICATION_REJECTED"})
        Status status,
        @Schema(description = "관리자 코멘트", example = "변경 요청을 승인합니다.", nullable = true)
        String adminComment,
        @Schema(description = "변경 요청자 정보")
        AdminUserInfo requestedBy,
        @Schema(description = "변경 요청 생성 일시", example = "2026-03-02T15:36:29")
        LocalDateTime createdAt
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

    @Builder
    @Schema(description = "변경 요청자 정보")
    public record AdminUserInfo(
            @Schema(description = "사용자 고유 ID", example = "1")
            Long userId,
            @Schema(description = "이메일 주소", example = "yukyum6@gmail.com")
            String email,
            @Schema(description = "이름", example = "이수아")
            String name
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