package DGU_AI_LAB.admin_be.domain.usedIds.repository;

import DGU_AI_LAB.admin_be.domain.usedIds.entity.UsedId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsedIdRepository extends JpaRepository<UsedId, Long> {
}

