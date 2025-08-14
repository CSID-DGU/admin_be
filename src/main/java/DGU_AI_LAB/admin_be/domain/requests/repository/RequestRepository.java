package DGU_AI_LAB.admin_be.domain.requests.repository;

import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RequestRepository extends JpaRepository<Request, Long> {
    List<Request> findAllByUser(User user);
    List<Request> findAllByUser_UserId(Long userId);
    List<Request> findAllByStatus(Status status);
    Optional<Request> findByUbuntuUsernameAndUbuntuPassword(String username, String passwordBase64);
    List<Request> findByUserUserIdAndStatus(Long userId, Status status);
    Optional<Request> findByUbuntuUsername(String ubuntuUsername);

    boolean existsByUbuntuUsername(String ubuntuUsername);
}
