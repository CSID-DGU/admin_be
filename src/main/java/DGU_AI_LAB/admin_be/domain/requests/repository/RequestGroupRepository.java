package DGU_AI_LAB.admin_be.domain.requests.repository;

import DGU_AI_LAB.admin_be.domain.requests.entity.RequestGroup;
import DGU_AI_LAB.admin_be.domain.requests.entity.RequestGroupId;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface RequestGroupRepository extends JpaRepository<RequestGroup, RequestGroupId> {
    List<RequestGroup> findAllByRequest_RequestId(Long requestId);

    /**
     * 이 그룹들을 고른 신청과 그 상태를 잠그며 읽는다. 잠그는 조회는 트랜잭션이 시작될 때의 스냅숏이 아니라
     * 지금 커밋된 행을 읽으므로, 그룹 행을 잠근 뒤에 부르면 다른 트랜잭션이 막 커밋한 신청까지 빠짐없이 본다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT rg FROM RequestGroup rg JOIN FETCH rg.request r WHERE rg.group.groupId IN :groupIds")
    List<RequestGroup> findAllByGroupIdsForUpdate(@Param("groupIds") Collection<Long> groupIds);

    /**
     * 승인 대기 그룹을 지우기 전에 그 그룹을 가리키는 신청 기록(거절·취소된 신청의 것)을 지운다. 그 사이 gid 가
     * 채워진 그룹이면 조건에서 빠져 아무것도 지우지 않는다.
     */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM RequestGroup rg WHERE rg.id.groupId IN "
            + "(SELECT g.groupId FROM Group g WHERE g.groupId = :groupId AND g.ubuntuGid IS NULL)")
    int deleteAllOfPendingGroup(@Param("groupId") Long groupId);
}
