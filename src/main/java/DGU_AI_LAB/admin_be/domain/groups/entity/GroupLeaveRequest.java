package DGU_AI_LAB.admin_be.domain.groups.entity;

import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 사용자가 낸 그룹 탈퇴 신청 한 건. 소속은 신청만으로 바뀌지 않는다 — 관리자가 승인하면 그룹 제거 작업이 등록되고
 * 그 작업이 성공해야 소속이 빠진다({@code GroupOperationService#requestRemove}).
 */
@Entity
@Getter
@Table(name = "group_leave_requests")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GroupLeaveRequest extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "group_leave_request_id")
    private Long groupLeaveRequestId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private GroupLeaveStatus status = GroupLeaveStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by")
    private User decidedBy;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    public static GroupLeaveRequest pending(User user, Group group) {
        GroupLeaveRequest request = new GroupLeaveRequest();
        request.user = user;
        request.group = group;
        return request;
    }

    public boolean isPending() {
        return status == GroupLeaveStatus.PENDING;
    }

    public void approve(User admin) {
        decide(GroupLeaveStatus.APPROVED, admin);
    }

    public void reject(User admin) {
        decide(GroupLeaveStatus.REJECTED, admin);
    }

    private void decide(GroupLeaveStatus decision, User admin) {
        this.status = decision;
        this.decidedBy = admin;
        this.decidedAt = LocalDateTime.now();
    }
}
