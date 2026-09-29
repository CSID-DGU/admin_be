package DGU_AI_LAB.admin_be.domain.monitoring.client;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Prometheus HTTP API의 instant query 호출. 주소는 설치 환경마다 다르므로 기본값 없이
 * {@code prometheus.base-url}로만 받는다 — 설정이 빠지면 엉뚱한 곳을 조용히 조회하지 않고 기동이 실패한다.
 */
@Component
public class PrometheusClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final WebClient webClient;

    @Autowired
    public PrometheusClient(@Value("${prometheus.base-url}") String baseUrl) {
        this(WebClient.builder().baseUrl(baseUrl).build());
    }

    PrometheusClient(WebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * 결과 벡터를 labelKey 값 → 수치로 돌려준다. 해당 라벨이 없는 시계열은 "unknown"으로 묶는다.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Double> queryVector(String promql, String labelKey) {
        Map<String, Object> response = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/query")
                        .queryParam("query", promql)
                        .build())
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(TIMEOUT)
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
            String key = metric == null ? "unknown" : metric.getOrDefault(labelKey, "unknown");
            out.put(key, Double.parseDouble(value.get(1).toString()));
        }
        return out;
    }
}
