package DGU_AI_LAB.admin_be.domain.warnings.repository;

import DGU_AI_LAB.admin_be.domain.warnings.entity.UserSuspension;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserSuspensionRepository extends JpaRepository<UserSuspension, Long> {

    /** 아직 끝나지 않은 정지 중 가장 늦게 끝나는 것. 있으면 그 사용자는 정지 중이다. */
    Optional<UserSuspension> findFirstByUser_UserIdAndEndsAtAfterOrderByEndsAtDesc(Long userId, LocalDateTime now);

    /**
     * 아직 끝나지 않은 정지를 잠금 읽기로 본다. 잠금 읽기는 트랜잭션이 시작된 뒤 커밋된 행까지 보므로, 사용자 행
     * 잠금을 기다리는 사이 부여된 정지를 놓치지 않는다.
     */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("SELECT s FROM UserSuspension s WHERE s.user.userId = :userId AND s.endsAt > :now")
    List<UserSuspension> findActiveForShare(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    Optional<UserSuspension> findByWarning_WarningId(Long warningId);

    @Query("SELECT DISTINCT s.user.userId FROM UserSuspension s WHERE s.endsAt > :now")
    List<Long> findSuspendedUserIds(@Param("now") LocalDateTime now);
}
