package DGU_AI_LAB.admin_be.domain.adminActionLog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "admin_action_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminActionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "admin_id", nullable = false)
    private Long adminId;

    @Column(name = "action_type", nullable = false, length = 50)
    private String actionType;

    @Column(name = "target_type", nullable = false, length = 30)
    private String targetType;

    @Column(name = "target_id", nullable = false, length = 255)
    private String targetId;

    @Column(name = "requested_role", length = 32)
    private String requestedRole;

    @Column(name = "http_status", nullable = false)
    private int httpStatus;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    public AdminActionLog(Long adminId, AdminActionType actionType, String targetId,
                          String requestedRole, int httpStatus, LocalDateTime occurredAt) {
        this.adminId = adminId;
        this.actionType = actionType.name();
        this.targetType = actionType.targetType().name();
        this.targetId = targetId;
        this.requestedRole = requestedRole;
        this.httpStatus = httpStatus;
        this.occurredAt = occurredAt;
    }
}