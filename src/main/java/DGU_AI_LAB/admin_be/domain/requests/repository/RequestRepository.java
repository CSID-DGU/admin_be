package DGU_AI_LAB.admin_be.domain.requests.repository;

import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
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
public interface RequestRepository extends JpaRepository<Request, Long> {

    /**
     * 신청서 메시지의 Slack 식별자만 적는다. 엔티티를 읽어 고치지 않는 것은 수정 시각(updated_at)을 건드리지 않기
     * 위해서다 — 그 시각은 홈 보존 기간 같은 판단의 기준이라 알림 때문에 바뀌면 안 된다.
     */
    @Modifying
    @Transactional
    @Query("UPDATE Request r SET r.slackMessageTs = :ts WHERE r.requestId = :requestId")
    int updateSlackMessageTs(@Param("requestId") Long requestId, @Param("ts") String ts);

    List<Request> findAllByUser_UserId(Long userId);
    List<Request> findAllByStatus(Status status);

    List<Request> findAllByUser_UserIdAndStatus(Long userId, Status status);
    boolean existsByUser_UbuntuUsernameAndUser_UserId(String ubuntuUsername, Long userId);
    boolean existsByPodName(String podName);
    boolean existsByUser_UbuntuUsernameAndStatusIn(String ubuntuUsername, Collection<Status> statuses);
    boolean existsByUser_UserIdAndStatus(Long userId, Status status);
    boolean existsByUser_UserIdAndStatusIn(Long userId, Collection<Status> statuses);
    long countByUser_UserIdAndStatusIn(Long userId, Collection<Status> statuses);

    /** 같은 사용자의 다른 신청 가운데 그 시각 이후까지 이어지는 것이 있는가. */
    boolean existsByUser_UserIdAndRequestIdNotAndStatusInAndExpiresAtGreaterThanEqual(
            Long userId, Long requestId, Collection<Status> statuses, LocalDateTime from);
    List<Request> findAllByStatusIn(List<Status> statuses);
    List<Request> findAllByUser_UserIdAndStatusIn(Long userId, List<Status> statuses);

    /**
     * 유저네임은 웹 계정(User)에 하나라 같은 유저네임의 종료된 신청(DENIED/DELETED) 이력이 계속 쌓인다. 따라서 유저네임으로
     * 신청을 찾을 때는 반드시 살아있는 상태로 범위를 좁혀야 하고, 그 안에서 가장 최근 건을
     * 쓴다. 정상 운영에서는 사용자당 살아있는 신청이 하나뿐이라 결과도 하나다.
     */
    @Query("SELECT r FROM Request r JOIN FETCH r.user u WHERE u.ubuntuUsername = :username AND r.status IN :statuses ORDER BY r.requestId DESC")
    List<Request> findByUbuntuUsernameAndStatusInOrderByRequestIdDesc(@Param("username") String username,
                                                                      @Param("statuses") List<Status> statuses);

    /**
     * 이 사용자의 리눅스 계정이 배포된 farm 노드가 남은 신청을 최신순으로 반환한다.
     * 계정 회수 작업에 node_name과 신청 번호를 넘기는 데 쓴다 — 노드를 안 넘기면 모든 farm 노드를
     * 훑어서 같은 유저네임의 무관한 레거시 계정까지 지운다. 정리가 끝난(DELETED) 신청도 nodeName을
     * 이력으로 남기므로, 컨테이너가 이미 만료돼 살아있는 신청이 없는 사용자도 여기서 노드를 되찾을 수 있다.
     */
    @Query("SELECT r FROM Request r WHERE r.user.userId = :userId AND r.nodeName IS NOT NULL ORDER BY r.requestId DESC")
    List<Request> findAllWithNodeByUserIdOrderByRequestIdDesc(@Param("userId") Long userId);


    @Query("SELECT r.user.ubuntuUsername FROM Request r WHERE r.status IN :statuses")
    List<String> findUbuntuUsernamesByStatusIn(@Param("statuses") List<Status> statuses);

    /**
     * 승인 처리 중 상태 확인 → 변경 사이의 race condition을 막기 위한 행 잠금 조회.
     * 동시에 같은 Request를 승인하려는 두 번째 트랜잭션은 첫 트랜잭션 커밋(짧은 상태-확인 트랜잭션) 때까지 대기한 뒤
     * PROCESSING 상태를 보고 INVALID_REQUEST_STATUS로 실패한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Request r WHERE r.requestId = :requestId")
    Optional<Request> findByIdForUpdate(@Param("requestId") Long requestId);

    /**
     * 관리자 신청 목록용. 응답 변환이 신청마다 읽는 사용자·서버·이미지만 함께 읽는다. 그룹처럼 여러 행인 연관은
     * 여기서 조인하지 않는다 — 두 개를 함께 조인하면 신청 하나가 (신청 그룹 수 × 계정 그룹 수)행으로 불어난다.
     * 그쪽은 응답을 만들 때 신청·사용자 번호로 한 번씩 묶어 읽는다(RequestQueryService.toResponseDTOs).
     */
    @Query("SELECT r FROM Request r " +
           "JOIN FETCH r.user " +
           "LEFT JOIN FETCH r.resourceGroup " +
           "LEFT JOIN FETCH r.containerImage " +
           "WHERE r.status IN :statuses AND r.createdAt >= :createdFrom " +
           "ORDER BY r.requestId DESC")
    List<Request> findAllForAdminList(@Param("statuses") Collection<Status> statuses,
                                      @Param("createdFrom") LocalDateTime createdFrom);

    /** 신청 하나를 응답 변환에 필요한 사용자·서버·이미지와 함께 읽는다. */
    @Query("SELECT r FROM Request r " +
           "JOIN FETCH r.user " +
           "LEFT JOIN FETCH r.resourceGroup " +
           "LEFT JOIN FETCH r.containerImage " +
           "WHERE r.requestId = :requestId")
    Optional<Request> findByIdForAdminView(@Param("requestId") Long requestId);

    /** 상태별 신청 건수. 건수가 0인 상태는 행이 없다. */
    @Query("SELECT r.status AS status, COUNT(r) AS count FROM Request r " +
           "WHERE r.createdAt >= :createdFrom GROUP BY r.status")
    List<StatusCount> countByStatusCreatedFrom(@Param("createdFrom") LocalDateTime createdFrom);

    interface StatusCount {
        Status getStatus();
        long getCount();
    }

    @Query("SELECT DISTINCT r FROM Request r " +
           "JOIN FETCH r.user u " +
           "LEFT JOIN FETCH r.resourceGroup " +
           "LEFT JOIN FETCH r.containerImage " +
           "LEFT JOIN FETCH r.requestGroups rg " +
           "LEFT JOIN FETCH rg.group " +
           "LEFT JOIN FETCH u.userGroups ug " +
           "LEFT JOIN FETCH ug.group " +
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
     * 트랜잭션이 안 걸려있어 resourceGroup(보상 트랜잭션 알림을 farm/lab 채널로 보내는 데 씀)과
     * user(알림의 유저네임)를 지연 로딩하면 LazyInitializationException이 나므로 함께 즉시 로딩한다.
     */
    @Query("SELECT r FROM Request r JOIN FETCH r.user JOIN FETCH r.resourceGroup WHERE r.status = :status AND r.updatedAt < :updatedAt")
    List<Request> findAllByStatusAndUpdatedAtBefore(@Param("status") Status status, @Param("updatedAt") LocalDateTime updatedAt);
}
