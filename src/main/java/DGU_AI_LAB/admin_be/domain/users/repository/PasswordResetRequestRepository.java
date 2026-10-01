package DGU_AI_LAB.admin_be.domain.users.repository;

import DGU_AI_LAB.admin_be.domain.users.entity.PasswordResetRequest;
import DGU_AI_LAB.admin_be.domain.users.entity.PasswordResetStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PasswordResetRequestRepository extends JpaRepository<PasswordResetRequest, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM PasswordResetRequest r WHERE r.passwordResetRequestId = :id")
    Optional<PasswordResetRequest> findByIdForUpdate(@Param("id") Long id);

    /** 엔티티를 싣지 않고 신청자만 읽는다 — 사용자 행을 먼저 잠근 뒤 신청을 최신 상태로 읽기 위해서다. */
    @Query("SELECT r.user.userId FROM PasswordResetRequest r WHERE r.passwordResetRequestId = :id")
    Optional<Long> findUserIdById(@Param("id") Long id);

    List<PasswordResetRequest> findAllByUser_UserIdAndStatusIn(Long userId, Collection<PasswordResetStatus> statuses);

    List<PasswordResetRequest> findAllByStatus(PasswordResetStatus status);

    @Query("SELECT r FROM PasswordResetRequest r JOIN FETCH r.user WHERE r.status IN :statuses ORDER BY r.createdAt DESC")
    List<PasswordResetRequest> findAllWithUserByStatusIn(@Param("statuses") Collection<PasswordResetStatus> statuses);

    /**
     * 잠금 읽기로 지금 커밋된 상태를 본다. 일반 조회는 트랜잭션이 처음 읽은 시점의 스냅샷을 보므로,
     * 사용자 행 잠금을 기다리는 동안 커밋된 신청을 놓친다.
     */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("SELECT r FROM PasswordResetRequest r WHERE r.user.userId = :userId AND r.status = :status")
    List<PasswordResetRequest> findAllByUserIdAndStatusForShare(@Param("userId") Long userId,
                                                                @Param("status") PasswordResetStatus status);
}
