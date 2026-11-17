package DGU_AI_LAB.admin_be.domain.adminActionLog.dto.response;

import DGU_AI_LAB.admin_be.domain.adminActionLog.AdminActionType;
import DGU_AI_LAB.admin_be.domain.adminActionLog.AdminTargetType;
import DGU_AI_LAB.admin_be.domain.adminActionLog.entity.AdminActionLog;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record AdminActionLogResponseDTO(
        Long id,
        String eventId,
        OffsetDateTime occurredAt,
        OffsetDateTime recordedAt,
        Long adminId,
        AdminActionType actionType,
        AdminTargetType targetType,
        String targetId,
        int httpStatus,
        String result,
        String requestedRole,
        String captureStatus,
        boolean executionResultTracked,
        String relatedStatusUrl,
        JsonNode requestValues) {
    public static AdminActionLogResponseDTO fromEntity(AdminActionLog log, JsonNode values) {
        int status = log.getHttpStatus();
        String result =
                status == 202
                        ? "ACCEPTED"
                        : status >= 200 && status < 300
                                ? "SUCCEEDED"
                                : status >= 400 && status < 600 ? "FAILED" : "OTHER";
        String url =
                log.getActionType() == AdminActionType.REQUEST_APPROVE
                                && log.getTargetId() != null
                                && log.getTargetId().matches("[0-9]+")
                        ? "/api/admin/requests/" + log.getTargetId() + "/provisioning-status"
                        : null;
        return new AdminActionLogResponseDTO(
                log.getId(),
                log.getEventId(),
                log.getOccurredAt().atOffset(ZoneOffset.UTC),
                log.getRecordedAt().atOffset(ZoneOffset.UTC),
                log.getAdminId(),
                log.getActionType(),
                log.getTargetType(),
                log.getTargetId(),
                status,
                result,
                log.getRequestedRole(),
                log.getCaptureStatus(),
                false,
                url,
                values);
    }
}
