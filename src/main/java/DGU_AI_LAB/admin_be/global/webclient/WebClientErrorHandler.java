package DGU_AI_LAB.admin_be.global.webclient;

import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;

import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public final class WebClientErrorHandler {

    // 응답 바디에 스택트레이스나 큰 JSON이 통째로 들어올 수 있어 로그 한 줄이 너무 길어지는
    // 것을 막는다 — 원인 파악에는 앞부분이면 충분하고, 전체가 필요하면 config-server 자체
    // 로그를 봐야 한다(어차피 여기 오는 body가 그쪽 로그의 일부다).
    private static final int LOGGED_BODY_MAX_LEN = 1000;

    private WebClientErrorHandler() {}

    public static WebClient.ResponseSpec onError(
            WebClient.ResponseSpec responseSpec,
            BiFunction<HttpStatusCode, String, RuntimeException> mapper
    ) {
        return responseSpec.onStatus(HttpStatusCode::isError, clientResponse -> {
                    HttpStatusCode status = clientResponse.statusCode();
                    return clientResponse.bodyToMono(String.class)
                            .defaultIfEmpty("")
                            .flatMap(body -> {
                                // 이 로그 한 줄만으로 "무엇을 호출했다가 몇 번으로 실패했고 몸통이
                                // 뭐였는지"가 남으므로, 각 호출부가 개별적으로 실패를 로깅하지
                                // 않아도 여기서 전부 확인 가능하다.
                                log.warn("[WEBCLIENT] 외부 API 오류 응답: status={} body={}",
                                        status, truncate(body));
                                RuntimeException ex = mapper.apply(status, body);
                                return ex != null ? Mono.error(ex) : Mono.empty();
                            });
                }
        );
    }

    /**
     * 인프라 서버가 요청 내용 때문에 거절한 4xx는 {@link ErrorCode#INFRA_REQUEST_REJECTED}(422)로, 서버 장애(5xx)는
     * 호출 목적의 오류 코드로 바꾼다. 4xx까지 502로 바꾸면 화면에서는 원인이 요청에 있는지 서버에 있는지 구분할 수 없다.
     */
    public static BusinessException rejectedOr(HttpStatusCode status, String body, String message, ErrorCode failureCode) {
        ErrorCode code = status.is4xxClientError() ? ErrorCode.INFRA_REQUEST_REJECTED : failureCode;
        return new BusinessException(message + ": " + body, code);
    }

    private static String truncate(String body) {
        if (body == null) return "";
        return body.length() > LOGGED_BODY_MAX_LEN
                ? body.substring(0, LOGGED_BODY_MAX_LEN) + "...(truncated)"
                : body;
    }
}
