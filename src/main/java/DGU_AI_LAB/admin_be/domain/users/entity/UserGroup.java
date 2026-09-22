package DGU_AI_LAB.admin_be.domain.users.entity;

import DGU_AI_LAB.admin_be.domain.groups.entity.Group;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 계정(User)이 실제로 가진 그룹 멤버십 — AD에 실제로 반영된 뒤에만(승인 성공 확인 후)
 * 커밋된다. {@link DGU_AI_LAB.admin_be.domain.requests.entity.RequestGroup}과 역할이
 * 다르다: RequestGroup은 "신규 컨테이너가 승인 전에 요청한 그룹"만 담당하고, "지금 이
 * 계정이 실제로 가진 그룹"을 답해야 하는 모든 읽기 경로는 이 테이블을 본다.
 */
@Entity
@Table(name = "user_groups")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = {"user", "group"})
public class UserGroup {

    @EmbeddedId
    private UserGroupId id;

    @ManyToOne(fetch = FetchType.LAZY) @MapsId("userId")
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY) @MapsId("groupId")
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder
    public UserGroup(User user, Group group) {
        this.user = user;
        this.group = group;
        this.id = new UserGroupId(); // Hibernate 6.x @MapsId: flush 전 id 객체가 non-null이어야 함
    }

    @PrePersist
    void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.id == null) {
            this.id = new UserGroupId(this.user.getUserId(), this.group.getGroupId());
        }
    }
}
