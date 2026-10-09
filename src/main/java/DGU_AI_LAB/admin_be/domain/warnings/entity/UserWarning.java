package DGU_AI_LAB.admin_be.domain.warnings.entity;

import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 경고 대장의 한 줄. 고치거나 지우지 않고 더하기만 한다 — 현재 횟수는 {@code WarningLedger}가 이 줄들을 처음부터
 * 다시 읽어 구한다. 종류는 {@link WarningType} 참고.
 */
@Entity
@Getter
@Table(name = "user_warnings")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserWarning extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "warning_id")
    private Long warningId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private WarningType type;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "issued_by", nullable = false)
    private User issuedBy;

    /** 취소(CANCEL) 줄만: 취소한 부여(GRANT) 줄. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "canceled_warning_id")
    private UserWarning canceledWarning;

    private UserWarning(WarningType type, User user, User issuedBy, String reason, UserWarning canceledWarning) {
        this.type = type;
        this.user = user;
        this.issuedBy = issuedBy;
        this.reason = reason;
        this.canceledWarning = canceledWarning;
    }

    public static UserWarning grant(User user, User admin, String reason) {
        return new UserWarning(WarningType.GRANT, user, admin, reason, null);
    }

    public static UserWarning deduct(User user, User admin, String reason) {
        return new UserWarning(WarningType.DEDUCT, user, admin, reason, null);
    }

    public static UserWarning cancel(UserWarning granted, User admin, String reason) {
        return new UserWarning(WarningType.CANCEL, granted.getUser(), admin, reason, granted);
    }

    public boolean isGrant() {
        return type == WarningType.GRANT;
    }
}
