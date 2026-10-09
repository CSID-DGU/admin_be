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

    /** 바꿀 대상 신청(컨테이너). 계정 단위 변경(PASSWORD)에는 없다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id")
    private Request request;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false, length = 20)
    private ChangeType changeType;

    @Column(name = "old_value", columnDefinition = "json")
    private String oldValue;

    /** PASSWORD에는 없다 — 새 비밀번호는 해시로만 PasswordResetRequest가 든다. */
    @Column(name = "new_value", columnDefinition = "json")
    private String newValue;

    /** PASSWORD에는 없다 — 메일 인증으로 본인만 확인한다. */
    @Column(name = "reason", length = 1000)
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

    /** 계정 단위 변경이라 대상 신청·새 값·사유가 없다. */
    public static ChangeRequest password(User requestedBy) {
        return new ChangeRequest(null, ChangeType.PASSWORD, null, null, null, requestedBy);
    }

    // ==== 비즈니스 메서드. ====
    public void approve(User admin, String comment) {
        this.status = Status.FULFILLED;
        this.reviewedBy = admin;
        this.adminComment = comment;
        this.reviewedAt = LocalDateTime.now();
    }

    /**
     * 승인했지만 반영이 작업으로 도는 중이다(공유 그룹 추가). 작업이 성공해야 {@link #completeProcessing()}으로
     * 승인이 끝난다. 검토자와 메모는 지금 남겨, 끝났을 때 안내에 쓴다.
     */
    public void startProcessing(User admin, String comment) {
        this.status = Status.PROCESSING;
        this.reviewedBy = admin;
        this.adminComment = comment;
        this.reviewedAt = LocalDateTime.now();
    }

    public void completeProcessing() {
        this.status = Status.FULFILLED;
    }

    /** 반영 작업이 실패했다. 다시 승인하거나 거절할 수 있게 승인 전으로 되돌린다. */
    public void returnToPending() {
        this.status = Status.PENDING;
        this.reviewedBy = null;
        this.adminComment = null;
        this.reviewedAt = null;
    }

    public void deny(User admin, String comment) {
        this.status = Status.DENIED;
        this.reviewedBy = admin;
        this.adminComment = comment;
        this.reviewedAt = LocalDateTime.now();
    }

    /** 신청자가 비활성화·탈퇴돼 검토 없이 닫는다. 검토자가 없는 DENIED로 남는다. */
    public void closeWithoutReview() {
        this.status = Status.DENIED;
    }
}
