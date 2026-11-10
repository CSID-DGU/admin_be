package DGU_AI_LAB.admin_be.domain.nodes.repository;

import DGU_AI_LAB.admin_be.domain.nodes.entity.Node;
import DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface NodeRepository extends JpaRepository<Node, String> {
    @Query("SELECT n FROM Node n LEFT JOIN FETCH n.gpus WHERE n.nodeId = :nodeId")
    Optional<Node> findByIdWithGpus(@Param("nodeId") String nodeId);

    List<Node> findAllByResourceGroup(ResourceGroup resourceGroup);
    List<Node> findAllByResourceGroupIn(Collection<ResourceGroup> resourceGroups);
}
