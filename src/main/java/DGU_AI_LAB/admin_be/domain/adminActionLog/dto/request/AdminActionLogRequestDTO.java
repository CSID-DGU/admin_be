package DGU_AI_LAB.admin_be.domain.adminActionLog.dto.request;

import DGU_AI_LAB.admin_be.domain.adminActionLog.AdminActionType;
import DGU_AI_LAB.admin_be.domain.adminActionLog.AdminTargetType;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

public record AdminActionLogRequestDTO(
        Long actionBy,
        AdminActionType actionType,
        AdminTargetType targetType,
        String targetId,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
        Integer page,
        Integer size
) {
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    public PageRequest toPageable() {
        int p = (page == null || page < 0) ? 0 : page;
        int s = (size == null || size < 1) ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        return PageRequest.of(p, s, Sort.by(Sort.Direction.DESC, "actionLogId"));
    }
}