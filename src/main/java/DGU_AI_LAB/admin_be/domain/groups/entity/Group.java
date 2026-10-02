package DGU_AI_LAB.admin_be.domain.groups.entity;

import DGU_AI_LAB.admin_be.domain.requests.entity.RequestGroup;
import DGU_AI_LAB.admin_be.domain.users.entity.UserGroup;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "`groups`")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Group {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "group_id")
    private Long groupId;

    // 그룹명은 인프라(우분투 그룹)와 1:1로 매칭되므로 DB에서도 유일해야 한다.
    @Column(name = "group_name", unique = true, nullable = false, length = 100)
    private String groupName;

    // 비어 있으면 승인 대기 그룹이다 — "새로 만들기"로 DB 에만 있고 인프라(원장·AD·팀 폴더)에는 아직 없다.
    // 이 그룹을 고른 신청의 생성 작업이 성공하면 config-server 가 발급한 gid 로 채운다(assignGid).
    @Column(name = "ubuntu_gid", unique = true)
    private Long ubuntuGid;

    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<RequestGroup> requestGroups = new HashSet<>();

    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<UserGroup> userGroups = new HashSet<>();

    @Builder
    public Group(String groupName, Long ubuntuGid) {
        this.groupName = groupName;
        this.ubuntuGid = ubuntuGid;
    }

    /** 인프라에 아직 만들어지지 않은 그룹인지. */
    public boolean isPending() {
        return ubuntuGid == null;
    }

    /** 생성 작업이 발급한 gid 를 채운다. 이미 다른 gid 가 있으면 기록을 덮어쓰지 않고 실패한다. */
    public void assignGid(Long gid) {
        if (gid == null || gid <= 0) {
            throw new BusinessException("그룹 gid 가 올바르지 않습니다: " + gid, ErrorCode.INVALID_INPUT_VALUE);
        }
        if (ubuntuGid != null && !ubuntuGid.equals(gid)) {
            throw new BusinessException(String.format("그룹 %s 에 이미 다른 gid(%d)가 있습니다: %d",
                    groupName, ubuntuGid, gid), ErrorCode.INVALID_REQUEST_STATUS);
        }
        this.ubuntuGid = gid;
    }

    // gid 로 비교하면 승인 대기 그룹(gid 없음)끼리 모두 같은 그룹이 되어 Set 에서 하나만 남는다. 그래서 DB 행
    // 번호로 비교한다. 저장 전(번호 없음)에는 같은 객체만 같다. 지연 로딩 프록시와도 맞도록 getter 로 읽는다.
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Group other)) {
            return false;
        }
        Long id = getGroupId();
        return id != null && id.equals(other.getGroupId());
    }

    // 저장되며 번호가 생겨도 해시가 바뀌지 않게 고정값을 쓴다.
    @Override
    public int hashCode() {
        return Group.class.hashCode();
    }
}
