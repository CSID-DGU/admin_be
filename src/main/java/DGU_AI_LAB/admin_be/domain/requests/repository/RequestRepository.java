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
    List<Request> findAllByUser_UserId(Long userId);
    List<Request> findAllByStatus(Status status);
    List<Request> findByUserUserIdAndStatus(Long userId, Status status);
    List<Request> findAllByUser_UserIdAndStatus(Long userId, Status status);
    boolean existsByUbuntuUsernameAndUser_UserId(String ubuntuUsername, Long userId);
    boolean existsByUser_UserIdAndStatusIn(Long userId, List<Status> statuses);
    List<Request> findAllByStatusIn(List<Status> statuses);
    List<Request> findAllByUser_UserIdAndStatusIn(Long userId, List<Status> statuses);

    /**
     * 유저네임이 웹 계정 단위로 고정되면서 ubuntu_username은 더 이상 유일하지 않다 —
     * 같은 유저네임의 종료된 신청(DENIED/DELETED) 이력이 계속 쌓인다. 따라서 유저네임으로
     * 신청을 찾을 때는 반드시 살아있는 상태로 범위를 좁혀야 하고, 그 안에서 가장 최근 건을
     * 쓴다. 정상 운영에서는 사용자당 살아있는 신청이 하나뿐이라 결과도 하나다.
     */
    @Query("SELECT r FROM Request r WHERE r.ubuntuUsername = :username AND r.status IN :statuses ORDER BY r.requestId DESC")
    List<Request> findByUbuntuUsernameAndStatusInOrderByRequestIdDesc(@Param("username") String username,
                                                                      @Param("statuses") List<Status> statuses);

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

    /**
     * 정지된(stale) PROCESSING/MIGRATING 요청 재조정 스케줄러용 — 마지막 갱신이
     * updatedAt 이전인, 즉 threshold보다 오래 방치된 요청을 찾는다. 스케줄러 메서드에는
     * 트랜잭션이 안 걸려있어 resourceGroup(보상 트랜잭션 알림을 farm/lab 채널로 보내는 데 씀)을
     * 지연 로딩하면 LazyInitializationException이 나므로 함께 즉시 로딩한다.
     */
    @Query("SELECT r FROM Request r JOIN FETCH r.resourceGroup WHERE r.status = :status AND r.updatedAt < :updatedAt")
    List<Request> findAllByStatusAndUpdatedAtBefore(@Param("status") Status status, @Param("updatedAt") LocalDateTime updatedAt);
}
