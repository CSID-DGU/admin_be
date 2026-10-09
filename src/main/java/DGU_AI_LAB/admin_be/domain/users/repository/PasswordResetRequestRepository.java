package DGU_AI_LAB.admin_be.domain.users.repository;

import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.users.entity.PasswordResetRequest;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 상태는 변경 요청(change_request)에 있으므로, 상태로 거르는 조회는 모두 변경 요청과 함께 읽는다. */
public interface PasswordResetRequestRepository extends JpaRepository<PasswordResetRequest, Long> {

    /** 변경 요청 행도 함께 잠가, 잠금을 기다리는 동안 커밋된 상태를 본다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM PasswordResetRequest r JOIN FETCH r.changeRequest c WHERE c.changeRequestId = :changeRequestId")
    Optional<PasswordResetRequest> findByChangeRequestIdForUpdate(@Param("changeRequestId") Long changeRequestId);

    /** 엔티티를 싣지 않고 신청자만 읽는다 — 사용자 행을 먼저 잠근 뒤 신청을 최신 상태로 읽기 위해서다. */
    @Query("SELECT r.user.userId FROM PasswordResetRequest r WHERE r.changeRequest.changeRequestId = :changeRequestId")
    Optional<Long> findUserIdByChangeRequestId(@Param("changeRequestId") Long changeRequestId);

    @Query("SELECT r FROM PasswordResetRequest r JOIN FETCH r.changeRequest c WHERE r.user.userId = :userId AND c.status IN :statuses")
    List<PasswordResetRequest> findAllByUserIdAndStatusIn(@Param("userId") Long userId,
                                                          @Param("statuses") Collection<Status> statuses);

    @Query("SELECT r FROM PasswordResetRequest r JOIN FETCH r.changeRequest c WHERE c.status = :status")
    List<PasswordResetRequest> findAllByStatus(@Param("status") Status status);

    /**
     * 잠금 읽기로 지금 커밋된 상태를 본다. 일반 조회는 트랜잭션이 처음 읽은 시점의 스냅샷을 보므로,
     * 사용자 행 잠금을 기다리는 동안 커밋된 신청을 놓친다.
     */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("SELECT r FROM PasswordResetRequest r JOIN FETCH r.changeRequest c WHERE r.user.userId = :userId AND c.status = :status")
    List<PasswordResetRequest> findAllByUserIdAndStatusForShare(@Param("userId") Long userId,
                                                                @Param("status") Status status);
}
