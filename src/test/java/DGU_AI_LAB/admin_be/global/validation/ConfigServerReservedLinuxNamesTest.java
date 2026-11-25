package DGU_AI_LAB.admin_be.global.validation;

import DGU_AI_LAB.admin_be.global.cluster.ClusterClientRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConfigServerReservedLinuxNamesTest {

    private final Map<String, Deque<Mono<ClientResponse>>> responses = new LinkedHashMap<>();
    private final ClusterClientRegistry clients = mock(ClusterClientRegistry.class);
    private final ConfigServerReservedLinuxNames names;

    ConfigServerReservedLinuxNamesTest() {
        when(clients.clusterIds()).thenReturn(Set.of("FARM", "LAB"));
        when(clients.configWebClient("FARM")).thenReturn(webClient("FARM"));
        when(clients.configWebClient("LAB")).thenReturn(webClient("LAB"));
        names = new ConfigServerReservedLinuxNames(clients);
    }

    private WebClient webClient(String clusterId) {
        responses.put(clusterId, new ArrayDeque<>());
        return WebClient.builder().exchangeFunction(request -> {
            assertThat(request.url().getPath()).isEqualTo("/reserved-names");
            return responses.get(clusterId).pop();
        }).build();
    }

    private void respond(String clusterId, HttpStatus status, String body) {
        responses.get(clusterId).add(Mono.just(ClientResponse.create(status)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build()));
    }

    @Test
    @DisplayName("받아 오기 전에는 아무 이름도 막지 않는다 — config-server가 계정 생성 때 다시 거른다")
    void beforeFirstRefresh_containsNothing() {
        assertThat(names.contains("root")).isFalse();
        assertThat(names.contains(null)).isFalse();
    }

    @Test
    @DisplayName("모든 config-server가 준 목록의 합집합을 예약 이름으로 본다")
    void refresh_loadsUnionFromEveryConfigServer() {
        respond("FARM", HttpStatus.OK, "{\"names\":[\"docker\",\"root\"]}");
        respond("LAB", HttpStatus.OK, "{\"names\":[\"root\",\"ubuntu\"]}");

        names.refresh();

        assertThat(names.contains("docker")).isTrue();
        assertThat(names.contains("ubuntu")).isTrue();
        assertThat(names.contains("alice")).isFalse();
    }

    @Test
    @DisplayName("한 클러스터 조회가 실패하거나 비어 있어도 각 클러스터의 마지막 성공 목록을 유지한다")
    void refresh_keepsEachClusterLastKnownNames() {
        respond("FARM", HttpStatus.OK, "{\"names\":[\"root\"]}");
        respond("LAB", HttpStatus.OK, "{\"names\":[\"ubuntu\"]}");
        names.refresh();

        respond("FARM", HttpStatus.INTERNAL_SERVER_ERROR, "{\"error\":\"boom\"}");
        respond("LAB", HttpStatus.OK, "{\"names\":[\"daemon\"]}");
        names.refresh();
        assertThat(names.contains("root")).isTrue();
        assertThat(names.contains("ubuntu")).isFalse();
        assertThat(names.contains("daemon")).isTrue();

        respond("FARM", HttpStatus.OK, "{\"names\":[]}");
        respond("LAB", HttpStatus.OK, "{\"names\":[\"daemon\"]}");
        names.refresh();
        assertThat(names.contains("root")).isTrue();

        responses.get("FARM").add(Mono.error(new IllegalStateException("connection refused")));
        respond("LAB", HttpStatus.OK, "{\"names\":[\"daemon\"]}");
        names.refresh();
        assertThat(names.contains("root")).isTrue();
    }
}
