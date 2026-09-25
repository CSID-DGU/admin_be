package DGU_AI_LAB.admin_be.domain.monitoring.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class PrometheusClientTest {

    private final AtomicReference<ClientRequest> captured = new AtomicReference<>();

    private PrometheusClient clientReturning(String json) {
        WebClient webClient = WebClient.builder()
                .baseUrl("http://prometheus.test")
                .exchangeFunction(request -> {
                    captured.set(request);
                    return Mono.just(ClientResponse.create(HttpStatus.OK)
                            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .body(json)
                            .build());
                })
                .build();
        return new PrometheusClient(webClient);
    }

    @Test
    @DisplayName("instant query 결과 벡터를 지정한 라벨 값 → 수치로 바꾼다")
    void queryVector_mapsByLabel() {
        PrometheusClient client = clientReturning("""
                {"status":"success","data":{"resultType":"vector","result":[
                  {"metric":{"Hostname":"node-a"},"value":[1700000000,"42.5"]},
                  {"metric":{"Hostname":"node-b"},"value":[1700000000,"7"]},
                  {"metric":{},"value":[1700000000,"1"]}
                ]}}
                """);

        var result = client.queryVector("avg by(Hostname)(X)", "Hostname");

        assertThat(result).containsExactly(
                java.util.Map.entry("node-a", 42.5),
                java.util.Map.entry("node-b", 7.0),
                java.util.Map.entry("unknown", 1.0));
        assertThat(captured.get().url().getPath()).isEqualTo("/api/v1/query");
        assertThat(captured.get().url().getQuery()).isEqualTo("query=avg by(Hostname)(X)");
    }

    @Test
    @DisplayName("data나 result가 없으면 빈 결과를 돌려준다")
    void queryVector_emptyWhenNoData() {
        assertThat(clientReturning("{\"status\":\"success\"}").queryVector("q", "cluster")).isEmpty();
        assertThat(clientReturning("{\"status\":\"success\",\"data\":{}}").queryVector("q", "cluster")).isEmpty();
    }
}
