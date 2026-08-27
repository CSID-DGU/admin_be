package DGU_AI_LAB.admin_be.domain.requests.repository;

import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
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
public interface RequestRepository extends JpaRepository<Request, Long> {

    List<Request> findAllByUser(User user);
    Optional<Request> findByUbuntuUsername(String username);
    List<Request> findAllByUser_UserId(Long userId);
    List<Request> findAllByStatus(Status status);
    List<Request> findByUserUserIdAndStatus(Long userId, Status status);
    boolean existsByUbuntuUsername(String ubuntuUsername);
    boolean existsByUbuntuUsernameAndStatusIn(String ubuntuUsername, List<Status> statuses);
    List<Request> findAllByUser_UserIdAndStatus(Long userId, Status status);
    boolean existsByUbuntuUsernameAndUser_UserId(String ubuntuUsername, Long userId);
    List<Request> findAllByStatusIn(List<Status> statuses);
    List<Request> findAllByUser_UserIdAndStatusIn(Long userId, List<Status> statuses);

    @Query("SELECT r.ubuntuUsername FROM Request r WHERE r.status = :status")
    List<String> findUbuntuUsernamesByStatus(@Param("status") Status status);

    @Query("SELECT r.ubuntuUsername FROM Request r WHERE r.status IN :statuses")
    List<String> findUbuntuUsernamesByStatusIn(@Param("statuses") List<Status> statuses);

    /**
     * 승인 처리 중 상태 확인 → 변경 사이의 race condition을 막기 위한 행 잠금 조회.
     * 동시에 같은 Request를 승인하려는 두 번째 트랜잭션은 첫 트랜잭션 커밋(짧은 상태-확인 트랜잭션) 때까지 대기한 뒤
     * PROCESSING 상태를 보고 INVALID_REQUEST_STATUS로 실패한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Request r WHERE r.requestId = :requestId")
    Optional<Request> findByIdForUpdate(@Param("requestId") Long requestId);

    @Query("SELECT DISTINCT r FROM Request r " +
           "JOIN FETCH r.user " +
           "LEFT JOIN FETCH r.resourceGroup " +
           "LEFT JOIN FETCH r.containerImage " +
           "LEFT JOIN FETCH r.requestGroups rg " +
           "LEFT JOIN FETCH rg.group " +
           "WHERE r.status = :status")
    List<Request> findAllByStatusWithAssociations(@Param("status") Status status);

    @Query("SELECT DISTINCT r FROM Request r " +
           "JOIN FETCH r.user " +
           "LEFT JOIN FETCH r.resourceGroup " +
           "LEFT JOIN FETCH r.containerImage " +
           "LEFT JOIN FETCH r.requestGroups rg " +
           "LEFT JOIN FETCH rg.group " +
           "WHERE r.status IN :statuses")
    List<Request> findAllByStatusInWithAssociations(@Param("statuses") List<Status> statuses);

    @Query("SELECT r FROM Request r JOIN FETCH r.user JOIN FETCH r.resourceGroup WHERE r.expiresAt BETWEEN :start AND :end AND r.status = :status")
    List<Request> findAllByExpiresAtBetweenAndStatus(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end, @Param("status") Status status);

    @Query("SELECT r FROM Request r JOIN FETCH r.user JOIN FETCH r.resourceGroup WHERE r.expiresAt BETWEEN :start AND :end AND r.status IN :statuses")
    List<Request> findAllByExpiresAtBetweenAndStatusIn(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end, @Param("statuses") List<Status> statuses);


    @Query("SELECT r FROM Request r JOIN FETCH r.user JOIN FETCH r.resourceGroup WHERE r.expiresAt < :now AND r.status = :status")
    List<Request> findAllWithUserByExpiredDateBefore(@Param("now") LocalDateTime now, @Param("status") Status status);
}
