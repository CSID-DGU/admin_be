package DGU_AI_LAB.admin_be.domain.requests.repository;

import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
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
    Optional<Request> findByUbuntuUsernameAndUbuntuPassword(String username, String passwordBase64);
    List<Request> findByUserUserIdAndStatus(Long userId, Status status);
    Optional<Request> findTopByUbuntuUsernameAndUbuntuUidIsNotNullOrderByApprovedAtDesc(String ubuntuUsername);
    boolean existsByUbuntuUsername(String ubuntuUsername);
    List<Request> findAllByUser_UserIdAndStatus(Long userId, Status status);
    boolean existsByUbuntuUsernameAndUser_UserId(String ubuntuUsername, Long userId);

    @Query("SELECT r.ubuntuUsername FROM Request r WHERE r.status = :status")
    List<String> findUbuntuUsernamesByStatus(@Param("status") Status status);

    @Query("SELECT r FROM Request r JOIN FETCH r.user JOIN FETCH r.resourceGroup WHERE r.expiresAt BETWEEN :start AND :end AND r.status = :status")
    List<Request> findAllByExpiresAtBetweenAndStatus(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end, @Param("status") Status status);


    @Query("SELECT r FROM Request r JOIN FETCH r.user JOIN FETCH r.resourceGroup WHERE r.expiresAt < :now AND r.status = 'FULFILLED'")
    List<Request> findAllWithUserByExpiredDateBefore(@Param("now") LocalDateTime now);
}