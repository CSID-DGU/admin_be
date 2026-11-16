package DGU_AI_LAB.admin_be.domain.adminActionLog.dto;

import DGU_AI_LAB.admin_be.domain.adminActionLog.entity.AdminActionLog;
import DGU_AI_LAB.admin_be.domain.adminActionLog.entity.AdminActionResult;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;

/** 기록 한 줄. 결과(result)는 저장된 값이 아니라 상태 코드로 계산한 값이다. */
public record AdminActionLogResponseDTO(
        Long id,
        LocalDateTime createdAt,
        String action,
        String targetType,
        String targetId,
        Long adminId,
        String adminName,
        AdminActionResult result,
        int httpStatus,
        String errorCode,
        String httpMethod,
        String requestUri,
        JsonNode pathVariables,
        JsonNode queryParams,
        JsonNode requestBody
) {
    public static AdminActionLogResponseDTO of(AdminActionLog log, JsonNode pathVariables, JsonNode queryParams,
                                               JsonNode requestBody) {
        return new AdminActionLogResponseDTO(
                log.getAdminActionLogId(), log.getCreatedAt(), log.getAction(), log.getTargetType(),
                log.getTargetId(), log.getAdminId(), log.getAdminName(), log.result(), log.getHttpStatus(),
                log.getErrorCode(), log.getHttpMethod(), log.getRequestUri(),
                pathVariables, queryParams, requestBody);
    }
}
