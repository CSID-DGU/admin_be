package DGU_AI_LAB.admin_be.domain.adminActionLog.repository;

import DGU_AI_LAB.admin_be.domain.adminActionLog.AdminActionType;
import DGU_AI_LAB.admin_be.domain.adminActionLog.AdminTargetType;
import DGU_AI_LAB.admin_be.domain.adminActionLog.entity.AdminActionLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AdminActionLogRepository extends JpaRepository<AdminActionLog, Long> {

    @Query("SELECT l FROM AdminActionLog l "
            + "WHERE (:actionBy IS NULL OR l.actionBy = :actionBy) "
            + "AND (:actionType IS NULL OR l.actionType = :actionType) "
            + "AND (:targetType IS NULL OR l.targetType = :targetType) "
            + "AND (:targetId IS NULL OR l.targetId = :targetId) "
            + "AND (:from IS NULL OR l.createdAt >= :from) "
            + "AND (:to IS NULL OR l.createdAt < :to)")
    List<AdminActionLog> search(@Param("actionBy") Long actionBy,
                                @Param("actionType") AdminActionType actionType,
                                @Param("targetType") AdminTargetType targetType,
                                @Param("targetId") String targetId,
                                @Param("from") LocalDateTime from,
                                @Param("to") LocalDateTime to,
                                Pageable pageable);
}