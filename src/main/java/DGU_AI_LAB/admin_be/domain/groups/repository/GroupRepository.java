package DGU_AI_LAB.admin_be.domain.groups.repository;

import DGU_AI_LAB.admin_be.domain.groups.entity.Group;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface GroupRepository extends JpaRepository<Group, Long> {
    List<Group> findAllByUbuntuGidIn(Set<Long> ubuntuGids);
    boolean existsByUbuntuGid(Long ubuntuGid);
    Optional<Group> findByUbuntuGid(Long ubuntuGid);
    boolean existsByGroupName(String groupName);

    Optional<Group> findByGroupName(String groupName);

    /**
     * 그룹 행을 잠근다. 승인 대기 그룹은 신청 저장·승인·결과 반영·삭제가 모두 이 잠금 뒤에 서로를 확인한다 —
     * 같은 그룹에 gid 가 두 번 발급되거나, 지우는 사이에 새 신청이 그 그룹을 가리키는 일을 막는다.
     * 여러 행을 잠그는 트랜잭션끼리 교착하지 않도록 번호 순서로 잠근다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT g FROM Group g WHERE g.groupId IN :groupIds ORDER BY g.groupId")
    List<Group> findAllByIdForUpdate(@Param("groupIds") Collection<Long> groupIds);

    /** 승인 대기 그룹만 지운다. 그 사이 gid 가 채워진(인프라에 만들어진) 그룹은 조건에서 빠져 지워지지 않는다. */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM Group g WHERE g.groupId = :groupId AND g.ubuntuGid IS NULL")
    int deletePendingById(@Param("groupId") Long groupId);
}
