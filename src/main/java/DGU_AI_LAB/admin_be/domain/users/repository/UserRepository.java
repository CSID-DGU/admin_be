package DGU_AI_LAB.admin_be.domain.users.repository;

import DGU_AI_LAB.admin_be.domain.users.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User,Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByUbuntuUsername(String ubuntuUsername);

    /**
     * 우분투 계정(UID/GID) 배정 시점의 "확인 후 배정" 경합을 막기 위한 행 잠금 조회.
     * 같은 사용자의 서로 다른 신청 두 건이 동시에 승인되면 둘 다 "아직 계정 없음"으로 보고
     * 각자 계정을 만들려 하는데, 배정 단계를 이 잠금으로 직렬화해야 한쪽만 배정하고
     * 다른 쪽은 이미 배정된 값을 확인하고 따라간다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.userId = :userId")
    Optional<User> findByIdForUpdate(@Param("userId") Long userId);

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
}
