package DGU_AI_LAB.admin_be.domain.gpus.repository;

import DGU_AI_LAB.admin_be.domain.gpus.entity.Gpu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GpuRepository extends JpaRepository<Gpu, Long> {

    // GPU 요약 프로젝션 (리소스 그룹 · GPU 기종 단위 집계)
    interface GpuSummary {
        Integer getRamGb();
        String getDescription();
        String getResourceGroupName();
        Long getNodeCount();
        Integer getRsgroupId();
        String getServerName();
    }

    /**
     * 리소스 그룹 · GPU 기종(ramGb)별로 노드 개수를 집계한다.
     *
     * GROUP BY에 n.nodeId가 들어가 있으면 그룹이 노드 단위로 쪼개져
     * COUNT(DISTINCT n.nodeId)가 항상 1이 되므로, 노드는 집계 대상으로만 남긴다.
     */
    @Query("""
        SELECT g.ramGb AS ramGb,
               rg.description AS description,
               rg.resourceGroupName AS resourceGroupName,
               COUNT(DISTINCT n.nodeId) AS nodeCount,
               rg.rsgroupId AS rsgroupId,
               rg.serverName AS serverName
        FROM Gpu g
        JOIN g.node n
        JOIN n.resourceGroup rg
        GROUP BY g.ramGb, rg.description, rg.resourceGroupName, rg.rsgroupId, rg.serverName
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
