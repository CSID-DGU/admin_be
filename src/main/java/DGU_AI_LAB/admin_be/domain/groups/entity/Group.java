package DGU_AI_LAB.admin_be.domain.groups.entity;

import DGU_AI_LAB.admin_be.domain.requests.entity.RequestGroup;
import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "`groups`")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "ubuntuGid")
public class Group {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "group_id")
    private Long groupId;

    // 그룹명은 인프라(우분투 그룹)와 1:1로 매칭되므로 DB에서도 유일해야 한다.
    @Column(name = "group_name", unique = true, nullable = false, length = 100)
    private String groupName;

    @Column(name = "ubuntu_gid", unique = true, nullable = false)
    private Long ubuntuGid;

    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<RequestGroup> requestGroups = new HashSet<>();

    @Builder
    public Group(String groupName, Long ubuntuGid) {
        this.groupName = groupName;
        this.ubuntuGid = ubuntuGid;
    }
}