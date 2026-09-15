package DGU_AI_LAB.admin_be.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("WebClientConfig — config-server 내부 API 토큰")
class WebClientConfigTest {

    /** 네트워크로 나가지 않고 요청만 가로채 헤더를 확인한다. mutate()는 기본 헤더를 그대로 유지한다. */
    private String sentToken(WebClient client) {
        AtomicReference<ClientRequest> seen = new AtomicReference<>();
        client.mutate()
                .exchangeFunction(request -> {
                    seen.set(request);
                    return Mono.just(ClientResponse.create(HttpStatus.OK).body("ok").build());
                })
                .build()
                .get().uri("/health").retrieve().bodyToMono(String.class).block();
        return seen.get().headers().getFirst(WebClientConfig.API_TOKEN_HEADER);
    }

    @Test
    @DisplayName("토큰이 설정돼 있으면 두 클라이언트 모두 헤더를 붙인다")
    void attachesTokenHeader() {
        WebClientConfig config = new WebClientConfig();

        assertThat(sentToken(config.configWebClient("http://config", 5, "s3cret"))).isEqualTo("s3cret");
        assertThat(sentToken(config.podWebClient("http://config", 5, "s3cret"))).isEqualTo("s3cret");
    }

    @Test
    @DisplayName("토큰이 비어 있으면 헤더를 붙이지 않는다")
    void noHeaderWhenBlank() {
        WebClient client = WebClientConfig.configServerClient("http://config", HttpClient.create(), " ");

        assertThat(sentToken(client)).isNull();
    }
}
