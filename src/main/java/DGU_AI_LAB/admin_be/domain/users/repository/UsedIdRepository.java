package DGU_AI_LAB.admin_be.domain.users.repository;

import DGU_AI_LAB.admin_be.domain.users.entity.UsedId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UsedIdRepository extends JpaRepository<UsedId,Long> {
    boolean existsById(Long id);

    @Query("SELECT MAX(u.uid) FROM UsedId u")
    Optional<Long> findMaxUid();
}