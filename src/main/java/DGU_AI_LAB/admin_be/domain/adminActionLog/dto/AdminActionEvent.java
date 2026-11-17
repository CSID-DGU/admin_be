package DGU_AI_LAB.admin_be.domain.adminActionLog.dto;

import DGU_AI_LAB.admin_be.domain.adminActionLog.AdminActionType;
import DGU_AI_LAB.admin_be.domain.adminActionLog.AdminTargetType;

import java.time.LocalDateTime;

/** 요청 스레드에서 확정한 값만 전달한다. 인증 객체·엔티티·요청 객체는 넘기지 않는다. */
public record AdminActionEvent(
        String eventId,
        LocalDateTime occurredAt,
        Long adminId,
        AdminActionType actionType,
        AdminTargetType targetType,
        String targetId,
        int httpStatus,
        String requestedRole,
        String captureStatus,
        String requestValues) {}
