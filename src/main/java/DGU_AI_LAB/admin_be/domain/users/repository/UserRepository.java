package DGU_AI_LAB.admin_be.domain.users.repository;

import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.users.entity.Role;
import DGU_AI_LAB.admin_be.domain.users.entity.UbuntuAccountStatus;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User,Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByUbuntuUsername(String ubuntuUsername);

    Optional<User> findByUbuntuUsername(String ubuntuUsername);

    List<User> findAllByUbuntuAccountStatus(UbuntuAccountStatus ubuntuAccountStatus);

    long countByRoleAndIsActiveTrue(Role role);

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
     * SSH 비밀번호 해시가 비어 있거나 지금 강도(currentPrefix로 시작)가 아닐 때만 바꾼다. 로그인은 트랜잭션 없이
     * 도므로 엔티티를 통째로 저장하면 그 사이 끝난 비밀번호 변경을 옛 값으로 덮어쓸 수 있다 — 변경은 늘 지금
     * 강도로 쓰므로, 이 조건이면 이 칸 하나만 그 변경과 겹치지 않고 바뀐다.
     */
    @Transactional
    @Modifying
    @Query("UPDATE User u SET u.ubuntuPasswordHash = :hash WHERE u.userId = :userId "
            + "AND (u.ubuntuPasswordHash IS NULL OR u.ubuntuPasswordHash NOT LIKE CONCAT(:currentPrefix, '%'))")
    int replaceWeakUbuntuPasswordHash(@Param("userId") Long userId, @Param("hash") String hash,
                                      @Param("currentPrefix") String currentPrefix);

    /**
     * 로그인 시각을 기록한다. 로그인은 트랜잭션 없이 돌아 엔티티를 고쳐도 저장되지 않는다. 엔티티를 통째로 저장하지
     * 않고 이 칸 하나만 바꿔, 그 사이 끝난 비밀번호 변경 등을 옛 값으로 덮어쓰지 않는다.
     */
    @Transactional
    @Modifying
    @Query("UPDATE User u SET u.lastLoginAt = :loggedInAt WHERE u.userId = :userId")
    int recordLogin(@Param("userId") Long userId, @Param("loggedInAt") LocalDateTime loggedInAt);

    /**
     * 장기 미사용 판정 후보. 가입 시각이 기준일보다 이르고, 끝나지 않은 신청(컨테이너 포함)이 하나도 없는 활성 일반
     * 사용자다. 관리자는 비활성화하면 운영할 사람이 사라지므로 뺀다. 미사용 기준 시각은 가입 시각보다 이르지 않아
     * 가입 시각으로 추려도 대상이 빠지지 않는다. 마지막 컨테이너가 끝난 시각까지 따진 최종 판정은
     * UserLifecycleTransactionalService가 한 곳에서 한다.
     */
    @Query("SELECT u FROM User u " +
            "WHERE u.isActive = true " +
            "  AND u.role <> DGU_AI_LAB.admin_be.domain.users.entity.Role.ADMIN " +
            "  AND u.createdAt < :thresholdDate " +
            "  AND NOT EXISTS (SELECT r FROM Request r WHERE r.user = u AND r.status IN :openStatuses)")
    List<User> findInactiveUsers(@Param("thresholdDate") LocalDateTime thresholdDate,
                                 @Param("openStatuses") Collection<Status> openStatuses);
}
