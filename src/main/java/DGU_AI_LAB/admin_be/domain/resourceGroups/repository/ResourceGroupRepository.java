package DGU_AI_LAB.admin_be.domain.resourceGroups.repository;

import DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ResourceGroupRepository extends JpaRepository<ResourceGroup, Integer> {
    Optional<ResourceGroup> findById(Integer rsgroupId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT rg FROM ResourceGroup rg WHERE rg.rsgroupId = :id")
    Optional<ResourceGroup> findByIdWithPessimisticLock(@Param("id") Integer id);
}
