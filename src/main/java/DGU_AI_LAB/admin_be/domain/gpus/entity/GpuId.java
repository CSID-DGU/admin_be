package DGU_AI_LAB.admin_be.domain.gpus.entity;

import java.io.Serializable;
import java.util.Objects;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GpuId implements Serializable {
    private Long gpuId;
    private String nodeId;
    private Long resourceGroup; // ResourceGroup의 PK

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GpuId that)) return false;
        return Objects.equals(gpuId, that.gpuId)
                && Objects.equals(nodeId, that.nodeId)
                && Objects.equals(resourceGroup, that.resourceGroup);
    }

    @Override
    public int hashCode() {
        return Objects.hash(gpuId, nodeId, resourceGroup);
    }
}

