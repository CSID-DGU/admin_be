package DGU_AI_LAB.admin_be.domain.home.repository;

import DGU_AI_LAB.admin_be.domain.home.entity.HomeCleanup;
import DGU_AI_LAB.admin_be.domain.home.entity.HomeCleanupStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface HomeCleanupRepository extends JpaRepository<HomeCleanup, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM HomeCleanup c WHERE c.homeCleanupId = :id")
    Optional<HomeCleanup> findByIdForUpdate(@Param("id") Long id);

    List<HomeCleanup> findAllByStatus(HomeCleanupStatus status);

    boolean existsByUser_UserIdAndStatus(Long userId, HomeCleanupStatus status);

    /** 그 종료 시각(또는 그 뒤)을 근거로 이미 진행 중이거나 끝난 시도가 있는가. */
    boolean existsByUser_UserIdAndStatusInAndLastContainerEndedAtGreaterThanEqual(
            Long userId, Collection<HomeCleanupStatus> statuses, LocalDateTime lastContainerEndedAt);
}
