package DGU_AI_LAB.admin_be.domain.adminActionLog.dto.response;

import DGU_AI_LAB.admin_be.domain.adminActionLog.AdminActionType;
import DGU_AI_LAB.admin_be.domain.adminActionLog.AdminTargetType;
import DGU_AI_LAB.admin_be.domain.adminActionLog.entity.AdminActionLog;

import java.time.LocalDateTime;

public record AdminActionLogResponseDTO(
        Long actionLogId,
        Long actionBy,
        AdminActionType actionType,
        AdminTargetType targetType,
        String targetId,
        String result,
        String changedValue,
        LocalDateTime createdAt
) {
    public static AdminActionLogResponseDTO fromEntity(AdminActionLog log) {
        return new AdminActionLogResponseDTO(
                log.getActionLogId(),
                log.getActionBy(),
                log.getActionType(),
                log.getTargetType(),
                log.getTargetId(),
                log.getResult(),
                log.getChangedValue(),
                log.getCreatedAt()
        );
    }
}