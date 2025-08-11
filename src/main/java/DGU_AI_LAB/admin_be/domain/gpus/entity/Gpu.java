package DGU_AI_LAB.admin_be.domain.gpus.entity;

import DGU_AI_LAB.admin_be.domain.nodes.entity.Node;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "gpus")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "gpuId")
public class Gpu {

    @Id
    @Column(name = "gpu_id", nullable = false)
    private Long gpuId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "node_id", nullable = false)
    private Node node;

    @Column(name = "gpu_model", nullable = false, length = 100)
    private String gpuModel;

    @Column(name = "RAM_GB", nullable = false)
    private Integer ramGb;
}
