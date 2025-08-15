package DGU_AI_LAB.admin_be.domain.gpus.repository;

import DGU_AI_LAB.admin_be.domain.gpus.entity.Gpu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GpuRepository extends JpaRepository<Gpu, Long> {

    // GPU 요약 프로젝션
    interface GpuSummary {
        String getGpuModel();
        Integer getRamGb();
        String getDescription();
        Long getNodeCount();
    }

    @Query("""
        SELECT g.gpuModel AS gpuModel,
               g.ramGb AS ramGb,
               rg.description AS description,
               COUNT(DISTINCT n.nodeId) AS nodeCount
        FROM Gpu g
        JOIN g.node n
        JOIN n.resourceGroup rg
        GROUP BY g.gpuModel, g.ramGb, rg.description
    """)
    List<GpuSummary> findGpuSummary();

    // GPU 모델별 노드 사양 조회
    interface NodeSpec {
        Integer getCpuCoreCount();
        Integer getMemorySizeGB();
    }

    @Query("""
        SELECT DISTINCT n.cpuCoreCount AS cpuCoreCount,
                        n.memorySizeGB AS memorySizeGB
        FROM Gpu g
        JOIN g.node n
        WHERE g.gpuModel = :gpuModel
    """)
    List<NodeSpec> findNodeSpecsByGpuModel(String gpuModel);
}