package DGU_AI_LAB.admin_be.domain.nodes.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@Table(name = "nodes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "nodeId")
public class Node {

    @Id
    @Column(name = "node_id", length = 100)
    private String nodeId;

    @Column(name = "rsgroup_id", nullable = false)
    private Integer rsgroupId;

    @Column(name = "memory_size_GB", nullable = false)
    private Integer memorySizeGB;

    @Column(name = "CPU_core_count", nullable = false)
    private Integer cpuCoreCount;
}
