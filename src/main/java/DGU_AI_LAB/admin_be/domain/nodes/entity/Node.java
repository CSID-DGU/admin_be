package DGU_AI_LAB.admin_be.domain.nodes.entity;

import DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "nodes")
@IdClass(NodeId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Node {

    @Id
    @Column(name = "node_id", length = 100, nullable = false)
    private String nodeId;

    @Id
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "rsgroup_id", nullable = false)
    private ResourceGroup resourceGroup;

    @Column(name = "memory_size_GB", nullable = false)
    private Integer memorySizeGB;

    @Column(name = "CPU_core_count", nullable = false)
    private Integer cpuCoreCount;
}
