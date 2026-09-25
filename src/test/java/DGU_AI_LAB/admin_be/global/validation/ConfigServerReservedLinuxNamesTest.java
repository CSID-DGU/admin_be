package DGU_AI_LAB.admin_be.global.validation;

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

import static org.assertj.core.api.Assertions.assertThat;

class ConfigServerReservedLinuxNamesTest {

    private final Deque<Mono<ClientResponse>> responses = new ArrayDeque<>();
    private final ConfigServerReservedLinuxNames names = new ConfigServerReservedLinuxNames(
            WebClient.builder().exchangeFunction(request -> {
                assertThat(request.url().getPath()).isEqualTo("/reserved-names");
                return responses.pop();
            }).build());

    private void respond(HttpStatus status, String body) {
        responses.add(Mono.just(ClientResponse.create(status)
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
    @DisplayName("config-server가 준 목록의 이름만 예약 이름으로 본다")
    void refresh_loadsNamesFromConfigServer() {
        respond(HttpStatus.OK, "{\"names\":[\"docker\",\"root\",\"ubuntu\"]}");

        names.refresh();

        assertThat(names.contains("docker")).isTrue();
        assertThat(names.contains("ubuntu")).isTrue();
        assertThat(names.contains("alice")).isFalse();
    }

    @Test
    @DisplayName("조회가 실패하거나 빈 목록이 오면 마지막으로 받은 목록을 유지한다")
    void refresh_keepsLastKnownNames_onFailureOrEmpty() {
        respond(HttpStatus.OK, "{\"names\":[\"root\"]}");
        names.refresh();

        respond(HttpStatus.INTERNAL_SERVER_ERROR, "{\"error\":\"boom\"}");
        names.refresh();
        assertThat(names.contains("root")).isTrue();

        respond(HttpStatus.OK, "{\"names\":[]}");
        names.refresh();
        assertThat(names.contains("root")).isTrue();

        responses.add(Mono.error(new IllegalStateException("connection refused")));
        names.refresh();
        assertThat(names.contains("root")).isTrue();
    }
}
