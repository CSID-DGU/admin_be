package DGU_AI_LAB.admin_be.domain.nodes.entity;

import DGU_AI_LAB.admin_be.domain.gpus.entity.Gpu;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NodeTest {

    @Test
    void getGpuModels_returnsEachModelOnceInOrder() {
        Node node = Node.builder().nodeId("FARM10").memorySizeGB(64).cpuCoreCount(16).build();
        node.getGpus().add(Gpu.builder().node(node).gpuModel("RTX A5000").ramGb(24).build());
        node.getGpus().add(Gpu.builder().node(node).gpuModel("RTX 3090").ramGb(24).build());
        node.getGpus().add(Gpu.builder().node(node).gpuModel("RTX A5000").ramGb(24).build());

        assertThat(node.getGpuModels()).containsExactly("RTX 3090", "RTX A5000");
        assertThat(node.getNumberGpu()).isEqualTo(3);
    }

    @Test
    void getGpuModels_isEmptyWithoutGpus() {
        Node node = Node.builder().nodeId("FARM10").memorySizeGB(64).cpuCoreCount(16).build();

        assertThat(node.getGpuModels()).isEmpty();
    }
}
