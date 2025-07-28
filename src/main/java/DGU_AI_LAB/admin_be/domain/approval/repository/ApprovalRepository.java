package DGU_AI_LAB.admin_be.domain.approval.repository;

import DGU_AI_LAB.admin_be.domain.approval.entity.Approval;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ApprovalRepository extends JpaRepository<Approval, Long> {
    Optional<Approval> findFirstByUsernameOrderByCreatedAtDesc(String username);
    Optional<Approval> findByUsername(String username);
}
