package DGU_AI_LAB.admin_be.domain.users.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "user_groups")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class UserGroup {
    @Id
    private Long gid;

    @Column(name = "groupName", nullable = false)
    private String groupName;

    @OneToOne
    @JoinColumn(name = "uid", referencedColumnName = "uid", nullable = false)
    private UsedId usedId;
}
