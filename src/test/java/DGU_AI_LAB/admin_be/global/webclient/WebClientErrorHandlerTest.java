package DGU_AI_LAB.admin_be.global.webclient;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WebClientErrorHandler는 실제 reactive 파이프라인(onStatus → bodyToMono → flatMap)을 타야
 * 의미가 있으므로, ExchangeFunction을 직접 스텁한 실제 WebClient로 검증한다.
 * (서비스 레이어 테스트들은 ResponseSpec.onStatus 자체를 mock으로 우회하기 때문에 이 로직을 검증하지 못한다.)
 */
@DisplayName("WebClientErrorHandler")
class WebClientErrorHandlerTest {

    private WebClient clientReturning(HttpStatus status, String body) {
        ExchangeFunction exchangeFunction = request -> Mono.just(
                ClientResponse.create(status)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
                        .body(body)
                        .build()
        );
        return WebClient.builder().exchangeFunction(exchangeFunction).build();
    }

    private WebClient.ResponseSpec retrieveFrom(HttpStatus status, String body) {
        return clientReturning(status, body).get().uri("/test").retrieve();
    }

    @Nested
    @DisplayName("정상 응답")
    class SuccessResponse {

        @Test
        @DisplayName("2xx 응답이면 mapper가 호출되지 않고 바디가 그대로 전달된다")
        void passesThroughSuccessBody() {
            WebClient.ResponseSpec spec = WebClientErrorHandler.onError(
                    retrieveFrom(HttpStatus.OK, "ok-body"),
                    (status, body) -> {
                        throw new AssertionError("2xx 응답에서는 mapper가 호출되면 안 된다");
                    }
            );

            StepVerifier.create(spec.bodyToMono(String.class))
                    .expectNext("ok-body")
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("4xx 응답")
    class ClientErrorResponse {

        @Test
        @DisplayName("mapper가 만든 예외로 스트림이 종료된다")
        void emitsMapperException() {
            RuntimeException expected = new IllegalStateException("bad request handled");

            WebClient.ResponseSpec spec = WebClientErrorHandler.onError(
                    retrieveFrom(HttpStatus.BAD_REQUEST, "invalid input"),
                    (status, body) -> {
                        assertThat(status).isEqualTo(HttpStatus.BAD_REQUEST);
                        assertThat(body).isEqualTo("invalid input");
                        return expected;
                    }
            );

            StepVerifier.create(spec.bodyToMono(String.class))
                    .expectErrorMatches(e -> e == expected)
                    .verify();
        }
    }

    @Nested
    @DisplayName("5xx 응답")
    class ServerErrorResponse {

        @Test
        @DisplayName("mapper가 만든 예외로 스트림이 종료된다")
        void emitsMapperException() {
            RuntimeException expected = new IllegalStateException("upstream down");

            WebClient.ResponseSpec spec = WebClientErrorHandler.onError(
                    retrieveFrom(HttpStatus.SERVICE_UNAVAILABLE, "unavailable"),
                    (status, body) -> {
                        assertThat(status).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                        assertThat(body).isEqualTo("unavailable");
                        return expected;
                    }
            );

            StepVerifier.create(spec.bodyToMono(String.class))
                    .expectErrorMatches(e -> e == expected)
                    .verify();
        }
    }

    @Nested
    @DisplayName("mapper가 null을 반환하는 특수 케이스 (예: 404를 '이미 없음'으로 간주)")
    class NullMapperResult {

        @Test
        @DisplayName("에러로 취급하지 않고, 다운스트림이 응답 바디를 성공 타입으로 그대로 읽는다")
        void doesNotErrorAndDownstreamReadsRawBody() {
            // onStatus의 exceptionFunction이 빈 Mono를 반환하면 WebClient는 에러로 취급하지 않고
            // 원래 응답을 그대로 다운스트림에 넘긴다 — "빈 스트림으로 완료"가 아니라
            // 이후 .bodyToMono(...)가 원본 바디를 성공 케이스처럼 파싱해서 값을 내보낸다.
            // (PodService.deletePod/UbuntuAccountService가 404를 무시할 때 이 경로를 그대로 탄다.)
            WebClient.ResponseSpec spec = WebClientErrorHandler.onError(
                    retrieveFrom(HttpStatus.NOT_FOUND, "not found"),
                    (status, body) -> {
                        assertThat(status).isEqualTo(HttpStatus.NOT_FOUND);
                        return null;
                    }
            );

            StepVerifier.create(spec.bodyToMono(String.class))
                    .expectNext("not found")
                    .verifyComplete();
        }
    }
}
