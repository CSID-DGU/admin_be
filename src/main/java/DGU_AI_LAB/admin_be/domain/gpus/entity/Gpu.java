package DGU_AI_LAB.admin_be.domain.gpus.entity;

import DGU_AI_LAB.admin_be.domain.nodes.entity.Node;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "gpus")
@IdClass(GpuId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Gpu {

    @Id
    @Column(name = "gpu_id", nullable = false)
    private Long gpuId;

    @Id
    @Column(name = "node_id", nullable = false, insertable = false, updatable = false)
    private String nodeId;

    @Id
    @Column(name = "rsgroup_id", nullable = false, insertable = false, updatable = false)
    private Long resourceGroup;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
            @JoinColumn(name = "node_id", referencedColumnName = "node_id"),
            @JoinColumn(name = "rsgroup_id", referencedColumnName = "rsgroup_id")
    })
    private Node node;

    @Column(name = "gpu_model", nullable = false, length = 100)
    private String gpuModel;

    @Column(name = "RAM_GB", nullable = false)
    private Integer ramGb;
}
