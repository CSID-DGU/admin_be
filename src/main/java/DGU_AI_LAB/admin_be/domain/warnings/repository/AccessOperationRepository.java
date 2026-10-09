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

    boolean existsByUser_UserIdAndStatus(Long userId, AccessOperationStatus status);

    /** 그 사용자에게 마지막으로 성공한 작업. 이 작업의 blocked 가 지금 실제로 적용된 상태다. */
    Optional<AccessOperation> findFirstByUser_UserIdAndStatusOrderByAccessOperationIdDesc(
            Long userId, AccessOperationStatus status);

    /** 지금 접속이 막혀 있는 사용자 — 마지막으로 성공한 작업이 차단인 사용자. */
    @Query("SELECT o.user.userId FROM AccessOperation o WHERE o.status = :applied AND o.blocked = true "
            + "AND o.accessOperationId = (SELECT MAX(latest.accessOperationId) FROM AccessOperation latest "
            + "WHERE latest.user = o.user AND latest.status = :applied)")
    List<Long> findBlockedUserIds(@Param("applied") AccessOperationStatus applied);
}
