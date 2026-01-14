package DGU_AI_LAB.admin_be.domain.users.repository;

import DGU_AI_LAB.admin_be.domain.users.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User,Long> {

    Optional<User> findByEmail(String email);

    /**
     * [자동 탈퇴 대상 조회 쿼리]
     * 조건:
     * 1. Active 상태인 유저
     * 2. (현재 - 마지막 로그인) > 3개월
     * 3. (현재 - 가장 최근 만료된 Pod 날짜) > 3개월 (Pod 사용 기록이 없으면 로그인 날짜만 봄)
     * * 주의: COALESCE를 사용하여 Pod 기록이 없으면 아주 먼 과거(1900년)로 취급해 조건 통과시킴
     */
    @Query("SELECT u FROM User u " +
            "LEFT JOIN u.requests r " +
            "WHERE u.isActive = true " +
            "GROUP BY u " +
            "HAVING " +
            "  (u.lastLoginAt IS NULL OR u.lastLoginAt < :thresholdDate) " +
            "  AND " +
            "  (MAX(r.expiresAt) IS NULL OR MAX(r.expiresAt) < :thresholdDate)")
    List<User> findInactiveUsers(@Param("thresholdDate") LocalDateTime thresholdDate);

    /**
     * [Hard Delete 대상 조회]
     * 조건: Soft Delete 된 지 1년 지난 유저
     */
    @Query("SELECT u FROM User u WHERE u.isActive = false AND u.deletedAt < :hardDeleteThreshold")
    List<User> findUsersForHardDelete(@Param("hardDeleteThreshold") LocalDateTime hardDeleteThreshold);
}
