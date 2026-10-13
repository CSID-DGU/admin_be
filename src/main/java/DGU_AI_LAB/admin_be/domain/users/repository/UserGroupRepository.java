package DGU_AI_LAB.admin_be.domain.users.repository;

import DGU_AI_LAB.admin_be.domain.users.entity.UserGroup;
import DGU_AI_LAB.admin_be.domain.users.entity.UserGroupId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface UserGroupRepository extends JpaRepository<UserGroup, UserGroupId> {
    List<UserGroup> findAllByUser_UserId(Long userId);

    /** 여러 계정의 그룹 소속을 한 번에 읽는다(계정마다 따로 읽지 않게). */
    @Query("SELECT ug FROM UserGroup ug JOIN FETCH ug.group WHERE ug.user.userId IN :userIds")
    List<UserGroup> findAllWithGroupByUserIds(@Param("userIds") Collection<Long> userIds);
}
