package DGU_AI_LAB.admin_be.domain.groups.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "`groups`")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Group {

    @Id
    @Column(name = "ubuntu_gid", nullable = false)
    private Long ubuntuGid;

    @Column(name = "group_name", nullable = false, length = 100)
    private String groupName;
}
