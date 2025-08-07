package DGU_AI_LAB.admin_be.domain.users.repository;

import DGU_AI_LAB.admin_be.domain.users.entity.UsedId;
import DGU_AI_LAB.admin_be.domain.users.entity.UserGroup;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserGroupRepository extends JpaRepository<UserGroup, Long> {
    Optional<UserGroup> findByGroupName(String groupName);
    Optional<UserGroup> findByUsedId(UsedId usedId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT MAX(u.gid) FROM UserGroup u")
    Optional<Long> findMaxGidWithLock();

    @Query("SELECT ug.usedId.uid FROM UserGroup ug WHERE ug.groupName = :groupName")
    Optional<Long> findUidByGroupName(@Param("groupName") String groupName);

    @Query("SELECT CASE WHEN COUNT(ug) > 0 THEN true ELSE false END FROM UserGroup ug WHERE ug.groupName = :groupName AND ug.usedId.uid = :uid")
    boolean existsByGroupNameAndUid(@Param("groupName") String groupName, @Param("uid") Long uid);

}