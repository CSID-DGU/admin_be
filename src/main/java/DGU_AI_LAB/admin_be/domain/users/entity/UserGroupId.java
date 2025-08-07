package DGU_AI_LAB.admin_be.domain.users.entity;

import lombok.*;

import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class UserGroupId implements Serializable {
    private Long gid;
    private Long usedId;
}