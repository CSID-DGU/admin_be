package DGU_AI_LAB.admin_be.domain.monitoring.service;

import DGU_AI_LAB.admin_be.domain.monitoring.client.PrometheusClient;
import DGU_AI_LAB.admin_be.domain.monitoring.dto.response.MonitoringMetricsResponseDTO;
import DGU_AI_LAB.admin_be.domain.monitoring.dto.response.MonitoringMetricsResponseDTO.GpuServer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 서버별 GPU 사용량·활성 컨테이너 지표. 지표 하나가 실패해도 나머지는 보여 주도록 각각 따로 비운다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MonitoringService {

    private static final String GPU_UTIL_BY_HOST = "avg by(Hostname)(DCGM_FI_DEV_GPU_UTIL)";
    private static final String GPU_COUNT_BY_HOST = "count by(Hostname)(DCGM_FI_DEV_GPU_UTIL)";
    private static final String RUNNING_CONTAINERS_BY_CLUSTER = "sum by(cluster)(cluster_monitor_container_running)";

    private final PrometheusClient prometheusClient;

    public MonitoringMetricsResponseDTO getMetrics() {
        return MonitoringMetricsResponseDTO.builder()
                .gpuServers(fetchGpuServers())
                .activeContainers(fetchActiveContainers())
                .build();
    }

    private List<GpuServer> fetchGpuServers() {
        try {
            Map<String, Double> utilMap = prometheusClient.queryVector(GPU_UTIL_BY_HOST, "Hostname");
            Map<String, Double> countMap = prometheusClient.queryVector(GPU_COUNT_BY_HOST, "Hostname");

            return utilMap.entrySet().stream()
                    .map(e -> GpuServer.builder()
                            .hostname(e.getKey())
                            .gpuUtil(Math.round(e.getValue() * 10.0) / 10.0)
                            .gpuCount(countMap.getOrDefault(e.getKey(), 0.0).intValue())
                            .build())
                    .sorted(Comparator.comparing(GpuServer::hostname))
                    .toList();
        } catch (Exception e) {
            // 빈 목록은 화면에서 "데이터 없음"과 구분되지 않는다 — 원인을 로그에 남긴다(admin_fe#169).
            log.warn("[MONITORING] GPU 지표 조회 실패: {}", e.toString());
            return List.of();
        }
    }

    private Map<String, Integer> fetchActiveContainers() {
        try {
            Map<String, Integer> result = new LinkedHashMap<>();
            prometheusClient.queryVector(RUNNING_CONTAINERS_BY_CLUSTER, "cluster")
                    .forEach((k, v) -> result.put(k, v.intValue()));
            return result;
        } catch (Exception e) {
            log.warn("[MONITORING] 활성 컨테이너 지표 조회 실패: {}", e.toString());
            return Map.of();
        }
    }
}
