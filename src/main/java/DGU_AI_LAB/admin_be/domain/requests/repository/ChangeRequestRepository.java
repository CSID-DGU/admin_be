package DGU_AI_LAB.admin_be.domain.requests.repository;

import DGU_AI_LAB.admin_be.domain.requests.entity.ChangeRequest;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChangeRequestRepository extends JpaRepository<ChangeRequest, Long> {
    List<ChangeRequest> findAllByStatus(Status status);
    List<ChangeRequest> findAllByRequestedBy_UserIdAndStatus(Long userId, Status status);
    List<ChangeRequest> findAllByRequestedBy_UserId(Long userId);

    /**
     * 변경 요청 승인 시 상태 확인 → 반영 사이의 race condition을 막기 위한 행 잠금 조회.
     * RequestRepository.findByIdForUpdate와 동일한 목적.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM ChangeRequest c WHERE c.changeRequestId = :changeRequestId")
    Optional<ChangeRequest> findByIdForUpdate(@Param("changeRequestId") Long changeRequestId);
}