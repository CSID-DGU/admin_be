// Prometheus에서 서버별 GPU 사용량·활성 컨테이너를 조회하는 공개 모니터링 엔드포인트
package DGU_AI_LAB.admin_be.domain.monitoring.controller;

import DGU_AI_LAB.admin_be.global.common.SuccessResponse;
import lombok.Builder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.*;

@RestController
@RequestMapping("/api/monitoring")
public class MonitoringController {

    private final WebClient prometheus;

    public MonitoringController(
            @Value("${prometheus.base-url:http://10.98.198.241:9090}") String prometheusUrl) {
        this.prometheus = WebClient.builder()
                .baseUrl(prometheusUrl)
                .build();
    }

    @Builder
    public record GpuServer(String hostname, double gpuUtil, int gpuCount) {}

    @Builder
    public record MetricsDTO(List<GpuServer> gpuServers, Map<String, Integer> activeContainers) {}

    @GetMapping("/metrics")
    public ResponseEntity<SuccessResponse<?>> getMetrics() {
        List<GpuServer> gpuServers = fetchGpuServers();
        Map<String, Integer> activeContainers = fetchActiveContainers();
        return SuccessResponse.ok(MetricsDTO.builder()
                .gpuServers(gpuServers)
                .activeContainers(activeContainers)
                .build());
    }

    @SuppressWarnings("unchecked")
    private List<GpuServer> fetchGpuServers() {
        try {
            // 서버별 평균 GPU 사용률
            Map<String, Double> utilMap = queryVector("avg by(Hostname)(DCGM_FI_DEV_GPU_UTIL)");
            // 서버별 GPU 개수
            Map<String, Double> countMap = queryVector("count by(Hostname)(DCGM_FI_DEV_GPU_UTIL)");

            return utilMap.entrySet().stream()
                    .map(e -> GpuServer.builder()
                            .hostname(e.getKey())
                            .gpuUtil(Math.round(e.getValue() * 10.0) / 10.0)
                            .gpuCount(countMap.getOrDefault(e.getKey(), 0.0).intValue())
                            .build())
                    .sorted(Comparator.comparing(GpuServer::hostname))
                    .toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Integer> fetchActiveContainers() {
        try {
            Map<String, Double> raw = queryVector("sum by(cluster)(cluster_monitor_container_running)");
            Map<String, Integer> result = new LinkedHashMap<>();
            raw.forEach((k, v) -> result.put(k, v.intValue()));
            return result;
        } catch (Exception e) {
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Double> queryVector(String promql) {
        Map<String, Object> response = prometheus.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/query")
                        .queryParam("query", promql)
                        .build())
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(5))
                .block();

        if (response == null) return Map.of();
        Map<String, Object> data = (Map<String, Object>) response.get("data");
        if (data == null) return Map.of();
        List<Map<String, Object>> results = (List<Map<String, Object>>) data.get("result");
        if (results == null) return Map.of();

        Map<String, Double> out = new LinkedHashMap<>();
        for (Map<String, Object> item : results) {
            Map<String, String> metric = (Map<String, String>) item.get("metric");
            List<Object> value = (List<Object>) item.get("value");
            // metric 키: Hostname(GPU) 또는 cluster(컨테이너)
            String key = metric.getOrDefault("Hostname", metric.getOrDefault("cluster", "unknown"));
            out.put(key, Double.parseDouble(value.get(1).toString()));
        }
        return out;
    }
}
