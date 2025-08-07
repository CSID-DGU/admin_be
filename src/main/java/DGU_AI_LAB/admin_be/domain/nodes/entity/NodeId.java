package DGU_AI_LAB.admin_be.domain.nodes.entity;

import lombok.*;

import java.io.Serializable;
import java.util.Objects;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NodeId implements Serializable {
    private String nodeId;
    private Long resourceGroup;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NodeId that)) return false;
        return Objects.equals(nodeId, that.nodeId) &&
                Objects.equals(resourceGroup, that.resourceGroup);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nodeId, resourceGroup);
    }
}
