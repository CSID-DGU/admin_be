package DGU_AI_LAB.admin_be.domain.users.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@IdClass(UserGroupId.class)
@Table(name = "user_groups")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class UserGroup {

    @Id
    private Long gid;

    @Id
    @OneToOne
    @JoinColumn(name = "uid", referencedColumnName = "uid", nullable = false, unique = true)
    private UsedId usedId;

    @Column(name = "groupName", nullable = false)
    private String groupName;
}