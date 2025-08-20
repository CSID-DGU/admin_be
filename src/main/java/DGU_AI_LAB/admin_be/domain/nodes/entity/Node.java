package DGU_AI_LAB.admin_be.domain.nodes.entity;

import DGU_AI_LAB.admin_be.domain.gpus.entity.Gpu;
import DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup;
import jakarta.persistence.*;
import lombok.*;

import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "nodes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "nodeId")
public class Node {

    /**
     * ex. LAB, FARM ...
     */
    @Id
    @Column(name = "node_id", length = 100)
    private String nodeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rsgroup_id", nullable = false)
    private ResourceGroup resourceGroup;

    @Column(name = "memory_size_GB", nullable = false)
    private Integer memorySizeGB;

    @Column(name = "CPU_core_count", nullable = false)
    private Integer cpuCoreCount;

    @OneToMany(mappedBy = "node", fetch = FetchType.LAZY)
    @Builder.Default
    private Set<Gpu> gpus = new LinkedHashSet<>();

    public int getNumberGpu() {
        return gpus == null ? 0 : gpus.size();
    }
}
