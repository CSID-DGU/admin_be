package DGU_AI_LAB.admin_be.global.webclient;

import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.function.BiFunction;

/**
 * WebClient 에러 응답(4xx/5xx) 처리 보일러플레이트를 공통화한다.
 * 각 호출부는 (상태코드, 응답 바디) -> 던질 예외를 결정하는 mapper만 넘기면 된다.
 * mapper가 null을 반환하면 에러로 취급하지 않고 빈 스트림으로 넘어간다
 * (예: 404를 "이미 삭제됨"으로 간주해 정상 처리하는 케이스).
 */
public final class WebClientErrorHandler {

    private WebClientErrorHandler() {}

    public static WebClient.ResponseSpec onError(
            WebClient.ResponseSpec responseSpec,
            BiFunction<HttpStatusCode, String, RuntimeException> mapper
    ) {
        return responseSpec.onStatus(HttpStatusCode::isError, clientResponse ->
                clientResponse.bodyToMono(String.class)
                        .flatMap(body -> {
                            RuntimeException ex = mapper.apply(clientResponse.statusCode(), body);
                            return ex != null ? Mono.error(ex) : Mono.empty();
                        })
        );
    }
}
