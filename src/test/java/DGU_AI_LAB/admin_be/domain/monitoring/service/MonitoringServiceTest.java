package DGU_AI_LAB.admin_be.domain.monitoring.service;

import DGU_AI_LAB.admin_be.domain.monitoring.client.PrometheusClient;
import DGU_AI_LAB.admin_be.domain.monitoring.dto.response.MonitoringMetricsResponseDTO;
import DGU_AI_LAB.admin_be.domain.monitoring.dto.response.MonitoringMetricsResponseDTO.GpuServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MonitoringServiceTest {

    @Mock
    private PrometheusClient prometheusClient;

    @InjectMocks
    private MonitoringService monitoringService;

    @Test
    @DisplayName("GPU 사용률은 소수 첫째 자리로 반올림하고 호스트 이름순으로 정렬해 개수와 합친다")
    void getMetrics_combinesGpuUtilAndCount() {
        Map<String, Double> util = new LinkedHashMap<>();
        util.put("node-b", 12.345);
        util.put("node-a", 50.0);
        when(prometheusClient.queryVector(startsWith("avg"), eq("Hostname"))).thenReturn(util);
        when(prometheusClient.queryVector(startsWith("count"), eq("Hostname"))).thenReturn(Map.of("node-a", 4.0));
        when(prometheusClient.queryVector(startsWith("sum"), eq("cluster"))).thenReturn(Map.of("farm", 3.0));

        MonitoringMetricsResponseDTO metrics = monitoringService.getMetrics();

        assertThat(metrics.gpuServers()).containsExactly(
                new GpuServer("node-a", 50.0, 4),
                new GpuServer("node-b", 12.3, 0));
        assertThat(metrics.activeContainers()).containsExactly(Map.entry("farm", 3));
    }

    @Test
    @DisplayName("한 지표 조회가 실패해도 다른 지표는 그대로 돌려준다")
    void getMetrics_isolatesFailures() {
        when(prometheusClient.queryVector(startsWith("avg"), eq("Hostname"))).thenThrow(new RuntimeException("down"));
        when(prometheusClient.queryVector(startsWith("sum"), eq("cluster"))).thenReturn(Map.of("lab", 2.0));

        MonitoringMetricsResponseDTO metrics = monitoringService.getMetrics();

        assertThat(metrics.gpuServers()).isEmpty();
        assertThat(metrics.activeContainers()).containsExactly(Map.entry("lab", 2));
    }
}
