package DGU_AI_LAB.admin_be.domain.warnings.repository;

import DGU_AI_LAB.admin_be.domain.warnings.entity.AccessOperation;
import DGU_AI_LAB.admin_be.domain.warnings.entity.AccessOperationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AccessOperationRepository extends JpaRepository<AccessOperation, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM AccessOperation o WHERE o.accessOperationId = :id")
    Optional<AccessOperation> findByIdForUpdate(@Param("id") Long id);

    List<AccessOperation> findAllByStatus(AccessOperationStatus status);

    boolean existsByUser_UserIdAndClusterIdAndStatus(
            Long userId, String clusterId, AccessOperationStatus status);

    boolean existsByUser_UserIdAndClusterIdAndStatusAndBlocked(
            Long userId, String clusterId, AccessOperationStatus status, boolean blocked);

    /** 그 사용자의 해당 클러스터에서 마지막으로 성공한 작업. 이 작업의 blocked 가 지금 실제 적용 상태다. */
    Optional<AccessOperation> findFirstByUser_UserIdAndClusterIdAndStatusOrderByAccessOperationIdDesc(
            Long userId, String clusterId, AccessOperationStatus status);

    /** 어느 클러스터에서든 마지막 성공 작업이 차단인 사용자. */
    @Query("SELECT DISTINCT o.user.userId FROM AccessOperation o WHERE o.status = :applied AND o.blocked = true "
            + "AND o.accessOperationId = (SELECT MAX(latest.accessOperationId) FROM AccessOperation latest "
            + "WHERE latest.user = o.user AND latest.clusterId = o.clusterId AND latest.status = :applied)")
    List<Long> findBlockedUserIds(@Param("applied") AccessOperationStatus applied);
}
