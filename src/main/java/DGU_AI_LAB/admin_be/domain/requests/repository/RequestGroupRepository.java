package DGU_AI_LAB.admin_be.domain.requests.repository;

import DGU_AI_LAB.admin_be.domain.requests.entity.RequestGroup;
import DGU_AI_LAB.admin_be.domain.requests.entity.RequestGroupId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RequestGroupRepository extends JpaRepository<RequestGroup, RequestGroupId> {
    List<RequestGroup> findAllByRequest_RequestId(Long requestId);
}
