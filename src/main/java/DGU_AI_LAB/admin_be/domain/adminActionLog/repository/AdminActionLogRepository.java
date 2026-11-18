package DGU_AI_LAB.admin_be.domain.adminActionLog.repository;

import DGU_AI_LAB.admin_be.domain.adminActionLog.entity.AdminActionLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 기록은 저장과 조회만 연다. {@code JpaRepository}를 쓰지 않아 delete·saveAll 같은 메서드가 아예 없다.
 */
public interface AdminActionLogRepository extends Repository<AdminActionLog, Long> {

    AdminActionLog save(AdminActionLog log);

    /**
     * 최신부터 커서 방식으로 읽는다. {@code cursor}보다 작은 번호만, {@code pageable}의 건수만큼.
     * 조건은 비어 있으면 거르지 않는다. {@code minStatus}를 400으로 주면 실패(4xx·5xx)만 나온다.대상으로 찾을 때는 (target_type, target_id),
     * 관리자로 찾을 때는 (admin_id, created_at) 인덱스를 탄다.
     */
    @Query("""
            select l from AdminActionLog l
            where (:targetType is null or l.targetType = :targetType)
              and (:targetId is null or l.targetId = :targetId)
              and (:adminId is null or l.adminId = :adminId)
              and (:action is null or l.action = :action)
              and (:from is null or l.createdAt >= :from)
              and (:to is null or l.createdAt < :to)
              and (:minStatus is null or l.httpStatus >= :minStatus)
              and (:cursor is null or l.adminActionLogId < :cursor)
            order by l.adminActionLogId desc
            """)
    List<AdminActionLog> search(@Param("targetType") String targetType,
                                @Param("targetId") String targetId,
                                @Param("adminId") Long adminId,
                                @Param("action") String action,
                                @Param("from") LocalDateTime from,
                                @Param("to") LocalDateTime to,
                                @Param("minStatus") Integer minStatus,
                                @Param("cursor") Long cursor,
                                Pageable pageable);
}
