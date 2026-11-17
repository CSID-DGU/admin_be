package DGU_AI_LAB.admin_be.domain.adminActionLog.web;

import DGU_AI_LAB.admin_be.domain.adminActionLog.AdminAction;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

/** 요청 하나에만 속하며, 안전한 값만 보관한다. */
final class AdminActionContext {
    static final String ATTRIBUTE = AdminActionContext.class.getName();
    final String eventId = UUID.randomUUID().toString();
    final LocalDateTime occurredAt = LocalDateTime.now(ZoneOffset.UTC);
    final AdminAction action;
    final Long adminId;
    final String targetId;
    final Map<String, Object> values = new LinkedHashMap<>();
    String captureStatus;
    String requestedRole;
    boolean submitted;

    AdminActionContext(AdminAction action, Long adminId, String targetId, String captureStatus) {
        this.action = action; this.adminId = adminId; this.targetId = targetId;
        this.captureStatus = captureStatus;
    }
}
