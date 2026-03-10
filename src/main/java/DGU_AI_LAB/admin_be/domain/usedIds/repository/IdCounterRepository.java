package DGU_AI_LAB.admin_be.domain.usedIds.repository;

import DGU_AI_LAB.admin_be.domain.usedIds.entity.CounterKey;
import DGU_AI_LAB.admin_be.domain.usedIds.entity.IdCounter;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface IdCounterRepository extends JpaRepository<IdCounter, CounterKey> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<IdCounter> findByKey(CounterKey key);
}
