package DGU_AI_LAB.admin_be.domain.users.repository;

import DGU_AI_LAB.admin_be.domain.users.entity.UserGroup;
import DGU_AI_LAB.admin_be.domain.users.entity.UserGroupId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserGroupRepository extends JpaRepository<UserGroup, UserGroupId> {
    List<UserGroup> findAllByUser_UserId(Long userId);
}
