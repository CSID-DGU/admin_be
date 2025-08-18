package DGU_AI_LAB.admin_be.domain.usedIds.repository;

import DGU_AI_LAB.admin_be.domain.usedIds.entity.UsedId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface UsedIdRepository extends JpaRepository<UsedId, Long> {
    @Query("SELECT MAX(u.idValue) FROM UsedId u")
    Optional<Long> findMaxIdValue();
}