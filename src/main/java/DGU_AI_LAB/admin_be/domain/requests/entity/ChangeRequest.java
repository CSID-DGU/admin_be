package DGU_AI_LAB.admin_be.domain.requests.entity;

import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChangeRequest extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "change_request_id")
    private Long changeRequestId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", nullable = false)
    private Request request;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false, length = 20)
    private ChangeType changeType;

    @Column(name = "old_value", columnDefinition = "json")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "json", nullable = false)
    private String newValue;

    @Column(name = "reason", length = 1000, nullable = false)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Column(name = "admin_comment", length = 500)
    private String adminComment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by", nullable = false)
    private User requestedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Builder
    public ChangeRequest(Request request, ChangeType changeType, String oldValue, String newValue, String reason, User requestedBy) {
        this.request = request;
        this.changeType = changeType;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.reason = reason;
        this.requestedBy = requestedBy;
    }

    // ==== 비즈니스 메서드. ====
    public void approve(User admin, String comment) {
        this.status = Status.FULFILLED;
        this.reviewedBy = admin;
        this.adminComment = comment;
        this.reviewedAt = LocalDateTime.now();
    }

    public void deny(User admin, String comment) {
        this.status = Status.DENIED;
        this.reviewedBy = admin;
        this.adminComment = comment;
        this.reviewedAt = LocalDateTime.now();
    }
}