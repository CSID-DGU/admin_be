package DGU_AI_LAB.admin_be.domain.groups.entity;

import DGU_AI_LAB.admin_be.domain.requests.entity.RequestGroup; // 임포트 추가
import DGU_AI_LAB.admin_be.domain.usedIds.entity.UsedId;
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

    @Column(name = "group_name", nullable = false, length = 100)
    private String groupName;

    @Column(name = "ubuntu_gid", unique = true, nullable = false)
    private Long ubuntuGid;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ubuntu_gid", referencedColumnName = "id_value", insertable = false, updatable = false)
    private UsedId usedId;

    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<RequestGroup> requestGroups = new HashSet<>();

    @Builder
    public Group(String groupName, Long ubuntuGid) {
        this.groupName = groupName;
        this.ubuntuGid = ubuntuGid;
    }
}