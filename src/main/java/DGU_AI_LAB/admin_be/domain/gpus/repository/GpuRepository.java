package DGU_AI_LAB.admin_be.domain.gpus.repository;

import DGU_AI_LAB.admin_be.domain.gpus.entity.Gpu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GpuRepository extends JpaRepository<Gpu, Long> {

    @Query("SELECT g.gpuModel, g.ramGb, rg.description, COUNT(DISTINCT n.nodeId) " +
            "FROM Gpu g JOIN g.node n JOIN ResourceGroup rg ON n.rsgroupId = rg.rsgroupId " +
            "GROUP BY g.gpuModel, g.ramGb, rg.description")
    List<Object[]> findGpuSummary();
    @Query("SELECT DISTINCT n.cpuCoreCount, n.memorySizeGB " +
            "FROM Gpu g JOIN g.node n " +
            "WHERE g.gpuModel = :gpuModel")
    List<Object[]> findNodeSpecsByGpuModel(String gpuModel);
}