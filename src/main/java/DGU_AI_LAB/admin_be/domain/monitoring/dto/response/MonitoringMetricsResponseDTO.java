package DGU_AI_LAB.admin_be.domain.monitoring.dto.response;

import lombok.Builder;

import java.util.List;
import java.util.Map;

@Builder
public record MonitoringMetricsResponseDTO(List<GpuServer> gpuServers, Map<String, Integer> activeContainers) {

    @Builder
    public record GpuServer(String hostname, double gpuUtil, int gpuCount) {
    }
}
