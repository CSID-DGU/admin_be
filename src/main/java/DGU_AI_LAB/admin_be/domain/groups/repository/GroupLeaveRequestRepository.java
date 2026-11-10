package DGU_AI_LAB.admin_be.domain.groups.repository;

import DGU_AI_LAB.admin_be.domain.groups.entity.GroupLeaveRequest;
import DGU_AI_LAB.admin_be.domain.groups.entity.GroupLeaveStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GroupLeaveRequestRepository extends JpaRepository<GroupLeaveRequest, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM GroupLeaveRequest r WHERE r.groupLeaveRequestId = :id")
    Optional<GroupLeaveRequest> findByIdForUpdate(@Param("id") Long id);

    boolean existsByUser_UserIdAndGroup_GroupIdAndStatus(Long userId, Long groupId, GroupLeaveStatus status);

    @Query("SELECT r FROM GroupLeaveRequest r JOIN FETCH r.user JOIN FETCH r.group WHERE r.status = :status ORDER BY r.createdAt")
    List<GroupLeaveRequest> findAllByStatusWithUserAndGroup(@Param("status") GroupLeaveStatus status);

    @Query("SELECT r FROM GroupLeaveRequest r JOIN FETCH r.user JOIN FETCH r.group WHERE r.user.userId = :userId ORDER BY r.createdAt DESC")
    List<GroupLeaveRequest> findAllByUserWithGroup(@Param("userId") Long userId);
}
