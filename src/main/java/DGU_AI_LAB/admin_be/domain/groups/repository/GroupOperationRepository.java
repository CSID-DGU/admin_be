package DGU_AI_LAB.admin_be.domain.groups.repository;

import DGU_AI_LAB.admin_be.domain.groups.entity.GroupOperation;
import DGU_AI_LAB.admin_be.domain.groups.entity.GroupOperationKind;
import DGU_AI_LAB.admin_be.domain.groups.entity.GroupOperationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GroupOperationRepository extends JpaRepository<GroupOperation, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM GroupOperation o WHERE o.groupOperationId = :id")
    Optional<GroupOperation> findByIdForUpdate(@Param("id") Long id);

    List<GroupOperation> findAllByStatus(GroupOperationStatus status);

    boolean existsByKindAndGroupNameAndStatus(GroupOperationKind kind, String groupName, GroupOperationStatus status);

    boolean existsByKindAndUser_UserIdAndGroup_GroupIdAndStatus(GroupOperationKind kind, Long userId, Long groupId,
                                                               GroupOperationStatus status);
}
