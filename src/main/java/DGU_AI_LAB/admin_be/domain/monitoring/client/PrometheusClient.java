package DGU_AI_LAB.admin_be.domain.monitoring.client;

import lombok.extern.slf4j.Slf4j;
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
@Slf4j
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

    /** 시계열 하나의 라벨 전체와 수치. */
    public record Sample(Map<String, String> labels, double value) {
    }

    /**
     * 결과 벡터를 labelKey 값 → 수치로 돌려준다. 해당 라벨이 없는 시계열은 "unknown"으로 묶는다.
     */
    public Map<String, Double> queryVector(String promql, String labelKey) {
        Map<String, Double> out = new LinkedHashMap<>();
        for (Sample s : querySeries(promql)) {
            out.put(s.labels().getOrDefault(labelKey, "unknown"), s.value());
        }
        return out;
    }

    /**
     * 결과 벡터를 라벨 전체와 함께 돌려준다. 값이 없거나 수로 읽히지 않는 시계열은 건너뛴다 —
     * 하나 때문에 나머지 결과까지 버리지 않는다.
     */
    @SuppressWarnings("unchecked")
    public List<Sample> querySeries(String promql) {
        Map<String, Object> response = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/query")
                        // PromQL의 {Hostname=~"…"} 같은 중괄호가 URI 템플릿 변수로 읽히지 않게 값은 변수로 넘긴다.
                        .queryParam("query", "{promql}")
                        .build(promql))
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(TIMEOUT)
                .block();

        if (response == null) return List.of();
        Map<String, Object> data = (Map<String, Object>) response.get("data");
        if (data == null) return List.of();
        List<Map<String, Object>> results = (List<Map<String, Object>>) data.get("result");
        if (results == null) return List.of();

        List<Sample> out = new java.util.ArrayList<>();
        for (Map<String, Object> item : results) {
            Map<String, String> metric = (Map<String, String>) item.get("metric");
            List<Object> value = (List<Object>) item.get("value");
            Double parsed = parseSample(value);
            if (parsed == null) {
                log.warn("[PrometheusClient] 읽을 수 없는 시계열을 건너뜀: labels={}, value={}", metric, value);
                continue;
            }
            out.add(new Sample(metric == null ? Map.of() : metric, parsed));
        }
        return out;
    }

    /** instant query의 value는 [시각, "수치"]다. 형식이 다르면 null. */
    private static Double parseSample(List<Object> value) {
        if (value == null || value.size() < 2 || value.get(1) == null) return null;
        try {
            double v = Double.parseDouble(value.get(1).toString());
            // Prometheus는 "NaN"·"+Inf"도 문자열로 돌려준다. 비유한 값은 수치로 쓸 수 없다.
            return Double.isFinite(v) ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
