package DGU_AI_LAB.admin_be.domain.requests.repository;

import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RequestRepository extends JpaRepository<Request, Long> {
    List<Request> findAllByUser_UserId(Long userId);
}