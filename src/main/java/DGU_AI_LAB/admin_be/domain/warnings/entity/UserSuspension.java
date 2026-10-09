package DGU_AI_LAB.admin_be.domain.warnings.entity;

import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 이용 정지 하나. 경고(부여) 하나가 정지 하나를 만든다. 끝나는 시각이 지나지 않은 정지가 하나라도 있으면 그 사용자는
 * 정지 중이다 — 정지가 겹치면 가장 늦게 끝나는 것이 실제 종료 시각이 된다.
 */
@Entity
@Getter
@Table(name = "user_suspensions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserSuspension extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "suspension_id")
    private Long suspensionId;

    @Column(name = "starts_at", nullable = false)
    private LocalDateTime startsAt;

    @Column(name = "ends_at", nullable = false)
    private LocalDateTime endsAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 이 정지를 만든 경고. */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warning_id", nullable = false)
    private UserWarning warning;

    public UserSuspension(UserWarning warning, LocalDateTime startsAt, int days) {
        this.user = warning.getUser();
        this.warning = warning;
        this.startsAt = startsAt;
        this.endsAt = startsAt.plusDays(days);
    }

    /** 아직 끝나지 않은 정지를 지금 끝낸다(원인이 된 경고가 취소됐을 때). 이미 끝난 정지는 그대로 둔다. */
    public void endNow(LocalDateTime now) {
        if (endsAt.isAfter(now)) {
            this.endsAt = now;
        }
    }
}
