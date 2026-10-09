package DGU_AI_LAB.admin_be.domain.warnings.repository;

import DGU_AI_LAB.admin_be.domain.warnings.entity.UserWarning;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserWarningRepository extends JpaRepository<UserWarning, Long> {

    List<UserWarning> findAllByUser_UserIdOrderByWarningIdAsc(Long userId);
}
