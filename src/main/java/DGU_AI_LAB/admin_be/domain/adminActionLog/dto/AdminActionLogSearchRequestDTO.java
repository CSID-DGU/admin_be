package DGU_AI_LAB.admin_be.domain.adminActionLog.dto;

import DGU_AI_LAB.admin_be.domain.adminActionLog.AdminActionType;
import DGU_AI_LAB.admin_be.domain.adminActionLog.AdminTargetType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * 기록 조회 조건. 모두 선택이며 비어 있으면 거르지 않는다.
 *
 * @param targetId 대상 식별자. targetType 과 함께 써야 한다
 * @param from     이 시각 이후(포함)
 * @param to       이 시각 이전(미포함)
 * @param failedOnly true 면 실패한 요청(http_status 400 이상: REJECTED·ERROR)만
 * @param cursor   이전 페이지의 nextCursor
 * @param size     한 번에 가져올 건수. 기본 20, 최대 100
 */
public record AdminActionLogSearchRequestDTO(
        AdminTargetType targetType,
        String targetId,
        Long adminId,
        AdminActionType action,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
        Boolean failedOnly,
        Long cursor,
        @Min(1) @Max(100) Integer size
) {
    public static final int DEFAULT_SIZE = 20;

    public boolean isFailedOnly() {
        return Boolean.TRUE.equals(failedOnly);
    }

    public int sizeOrDefault() {
        return size == null ? DEFAULT_SIZE : size;
    }
}
