package DGU_AI_LAB.admin_be.domain.nodes.repository;

import DGU_AI_LAB.admin_be.domain.nodes.entity.Node;
import DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NodeRepository extends JpaRepository<Node, Long> {
    List<Node> findAllByResourceGroup(ResourceGroup resourceGroup);
}
