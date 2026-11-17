package DGU_AI_LAB.admin_be.domain.adminActionLog.entity;

import DGU_AI_LAB.admin_be.domain.adminActionLog.AdminActionType;
import DGU_AI_LAB.admin_be.domain.adminActionLog.AdminTargetType;
import DGU_AI_LAB.admin_be.domain.adminActionLog.dto.AdminActionEvent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Getter
@Entity
@Table(name = "admin_action_logs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminActionLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
            name = "event_id",
            nullable = false,
            unique = true,
            length = 36,
            columnDefinition = "CHAR(36)")
    private String eventId;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    @Column(name = "admin_id")
    private Long adminId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 64)
    private AdminActionType actionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 32)
    private AdminTargetType targetType;

    @Column(name = "target_id", length = 255)
    private String targetId;

    @Column(name = "http_status", nullable = false)
    private short httpStatus;

    @Column(name = "requested_role", length = 32)
    private String requestedRole;

    @Column(name = "capture_status", nullable = false, length = 32)
    private String captureStatus;

    @Column(name = "request_values", nullable = false, columnDefinition = "TEXT")
    private String requestValues;

    public static AdminActionLog from(AdminActionEvent e) {
        AdminActionLog log = new AdminActionLog();
        log.eventId = e.eventId();
        log.occurredAt = e.occurredAt();
        log.recordedAt = LocalDateTime.now(ZoneOffset.UTC);
        log.adminId = e.adminId();
        log.actionType = e.actionType();
        log.targetType = e.targetType();
        log.targetId = e.targetId();
        log.httpStatus = (short) e.httpStatus();
        log.requestedRole = e.requestedRole();
        log.captureStatus = e.captureStatus();
        log.requestValues = e.requestValues();
        return log;
    }
}
