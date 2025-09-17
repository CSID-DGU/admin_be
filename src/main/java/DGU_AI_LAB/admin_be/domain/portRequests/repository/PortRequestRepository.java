package DGU_AI_LAB.admin_be.domain.portRequests.repository;

import DGU_AI_LAB.admin_be.domain.portRequests.entity.PortRequests;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PortRequestRepository extends JpaRepository<PortRequests, Long> {

    List<PortRequests> findByRequestRequestId(Long requestId);

    List<PortRequests> findByResourceGroupRsgroupId(Integer resourceGroupId);

    boolean existsByPortNumberAndResourceGroupRsgroupId(Integer portNumber, Integer resourceGroupId);

    @Query("SELECT p.portNumber FROM PortRequests p WHERE p.resourceGroup.rsgroupId = :resourceGroupId ORDER BY p.portNumber ASC")
    List<Integer> findPortNumbersByResourceGroupRsgroupIdOrderByPortNumberAsc(@Param("resourceGroupId") Integer resourceGroupId);

}