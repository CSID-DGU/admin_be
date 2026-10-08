package DGU_AI_LAB.admin_be.domain.portRequests.repository;

import DGU_AI_LAB.admin_be.domain.portRequests.entity.PortOperation;
import DGU_AI_LAB.admin_be.domain.portRequests.entity.PortOperationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PortOperationRepository extends JpaRepository<PortOperation, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM PortOperation o WHERE o.portOperationId = :id")
    Optional<PortOperation> findByIdForUpdate(@Param("id") Long id);

    List<PortOperation> findAllByStatus(PortOperationStatus status);

    boolean existsByRequest_RequestIdAndStatus(Long requestId, PortOperationStatus status);
}
