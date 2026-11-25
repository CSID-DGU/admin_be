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
    /**
     * 작업 결과 폴러는 트랜잭션 밖에서 신청의 클러스터를 골라야 한다. ResourceGroup을 함께 읽지 않으면
     * {@code Request.getServerName()}이 지연 로딩 프록시를 열다가 실패해 결과를 영원히 반영하지 못한다.
     */
    @Query("SELECT r FROM Request r JOIN FETCH r.resourceGroup WHERE r.status = :status")
    List<Request> findAllByStatus(@Param("status") Status status);
    List<Request> findAllByTestTrueAndStatusOrderByRequestIdAsc(Status status);

    List<Request> findAllByUser_UserIdAndStatus(Long userId, Status status);
    boolean existsByPodName(String podName);
    boolean existsByPodNameAndClusterIdIgnoreCase(String podName, String clusterId);
    /** GPU 프로세스 소유자 해석용. 같은 Pod 이름의 다른 클러스터·노드·종료 신청을 섞지 않는다. */
    @Query("SELECT r FROM Request r JOIN FETCH r.user " +
           "WHERE r.podName IN :podNames " +
           "AND LOWER(r.clusterId) = LOWER(:clusterId) " +
           "AND LOWER(r.nodeName) = LOWER(:nodeName) " +
           "AND r.status IN :statuses")
    List<Request> findAllByPodNameInAndClusterIdAndNodeNameAndStatusIn(
            @Param("podNames") Collection<String> podNames,
            @Param("clusterId") String clusterId,
            @Param("nodeName") String nodeName,
            @Param("statuses") Collection<Status> statuses);

    @Query("SELECT r FROM Request r JOIN FETCH r.user WHERE r.requestId = :requestId")
    Optional<Request> findByIdWithUser(@Param("requestId") Long requestId);
    /**
     * Pod 이름에서 읽은 유저네임이 같은 클러스터의 서버별 계정에 속하고, 그 사용자의 같은 클러스터 신청이
     * 생성·이동 중인가. User.ubuntuUsername은 FARM/LAB에서 이름이 다른 이전 계정을 표현하지 못하므로 쓰지 않는다.
     */
    @Query("SELECT COUNT(r) > 0 FROM Request r JOIN r.user u JOIN u.ubuntuAccounts a " +
           "WHERE a.username = :username AND LOWER(a.serverName) = LOWER(:clusterId) " +
           "AND LOWER(r.clusterId) = LOWER(:clusterId) AND r.status IN :statuses")
    boolean existsByUbuntuAccountUsernameAndClusterIdAndStatusIn(
            @Param("username") String username,
            @Param("clusterId") String clusterId,
            @Param("statuses") Collection<Status> statuses);
    boolean existsByUser_UserIdAndStatus(Long userId, Status status);
    boolean existsByUser_UserIdAndStatusIn(Long userId, Collection<Status> statuses);
    long countByUser_UserIdAndStatusIn(Long userId, Collection<Status> statuses);

    /** 같은 사용자의 다른 신청 가운데 그 시각 이후까지 이어지는 것이 있는가. */
    boolean existsByUser_UserIdAndRequestIdNotAndStatusInAndExpiresAtGreaterThanEqual(
            Long userId, Long requestId, Collection<Status> statuses, LocalDateTime from);
    List<Request> findAllByStatusIn(List<Status> statuses);
    List<Request> findAllByUser_UserIdAndStatusIn(Long userId, List<Status> statuses);

    /**
     * 서버별 UbuntuAccount의 유저네임과 그 서버의 신청이 함께 맞는 열린 신청만 찾는다. 계정의 FARM 이름이
     * LAB 신청과 교차 결합되지 않도록 account.serverName과 Request의 불변 clusterId를 같은 값으로 묶는다.
     */
    @Query("SELECT DISTINCT r FROM Request r JOIN FETCH r.user u JOIN u.ubuntuAccounts a " +
           "WHERE a.username = :username AND LOWER(a.serverName) = LOWER(r.clusterId) " +
           "AND r.status IN :statuses ORDER BY r.requestId DESC")
    List<Request> findByUbuntuAccountUsernameAndStatusInOrderByRequestIdDesc(
            @Param("username") String username,
            @Param("statuses") List<Status> statuses);

    /** Config Server의 레거시 username 조회도 호출 클러스터의 UbuntuAccount로만 신청을 고른다. */
    @Query("SELECT DISTINCT r FROM Request r JOIN FETCH r.user u JOIN u.ubuntuAccounts a " +
           "WHERE a.username = :username AND LOWER(a.serverName) = LOWER(:clusterId) " +
           "AND LOWER(r.clusterId) = LOWER(:clusterId) " +
           "AND r.status IN :statuses ORDER BY r.requestId DESC")
    List<Request> findByUbuntuAccountUsernameAndClusterIdAndStatusInOrderByRequestIdDesc(
            @Param("username") String username,
            @Param("clusterId") String clusterId,
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
