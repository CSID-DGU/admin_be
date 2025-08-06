package DGU_AI_LAB.admin_be.domain.users.repository;

import DGU_AI_LAB.admin_be.domain.users.entity.UserGroup;
import DGU_AI_LAB.admin_be.domain.users.entity.UserGroupId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface UserGroupRepository extends JpaRepository<UserGroup, UserGroupId> {

    Optional<UserGroup> findByGroupName(String groupName);

    @Query("SELECT u.usedId.uid FROM UserGroup u WHERE u.groupName = :groupName")
    Optional<Long> findUidByGroupName(String groupName);

    @Query("SELECT COUNT(u) > 0 FROM UserGroup u WHERE u.groupName = :groupName AND u.usedId.uid = :uid")
    boolean existsByGroupNameAndUid(String groupName, Long uid);
}
