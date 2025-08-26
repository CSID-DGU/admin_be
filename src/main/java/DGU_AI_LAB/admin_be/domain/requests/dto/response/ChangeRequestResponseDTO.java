package DGU_AI_LAB.admin_be.domain.requests.dto.response;

import DGU_AI_LAB.admin_be.domain.requests.entity.ChangeRequest;
import DGU_AI_LAB.admin_be.domain.requests.entity.ChangeType;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import com.fasterxml.jackson.annotation.JsonRawValue;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record ChangeRequestResponseDTO(
        Long changeRequestId,
        Long originalRequestId,
        ChangeType changeType,
        @JsonRawValue String oldValue,
        @JsonRawValue String newValue,
        String reason,
        Status status,
        AdminUserInfo requestedBy,
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
                .requestedBy(AdminUserInfo.fromEntity(changeRequest.getRequestedBy()))
                .createdAt(changeRequest.getCreatedAt())
                .build();
    }

    @Builder
    public record AdminUserInfo(
            Long userId,
            String email,
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