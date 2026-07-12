package DGU_AI_LAB.admin_be.domain.portRequests.repository;

import DGU_AI_LAB.admin_be.domain.portRequests.entity.PortRequests;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface PortRequestRepository extends JpaRepository<PortRequests, Long> {

    List<PortRequests> findByRequestRequestId(Long requestId);

    List<PortRequests> findByRequestRequestIdIn(Collection<Long> requestIds);

    List<PortRequests> findByResourceGroupRsgroupId(Integer resourceGroupId);


}