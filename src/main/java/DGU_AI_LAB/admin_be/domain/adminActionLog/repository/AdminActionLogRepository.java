package DGU_AI_LAB.admin_be.domain.adminActionLog.repository;

import DGU_AI_LAB.admin_be.domain.adminActionLog.entity.AdminActionLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminActionLogRepository extends JpaRepository<AdminActionLog, Long> {
}