package DGU_AI_LAB.admin_be.domain.users.service;

import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UbuntuPasswordSyncClientTest {

    private static final String HASH = "$6$salt$hash";

    private final List<ClientRequest> sent = new ArrayList<>();

    private UbuntuPasswordSyncClient clientReturning(HttpStatus status) {
        WebClient webClient = WebClient.builder()
                .baseUrl("http://config-server")
                .exchangeFunction(request -> {
                    sent.add(request);
                    return Mono.just(ClientResponse.create(status)
                            .header("Content-Type", "application/json")
                            .body("{\"error\":\"X\"}")
                            .build());
                })
                .build();
        return new UbuntuPasswordSyncClient(webClient);
    }

    @Test
    @DisplayName("PUT /users/{username}/password 로 보낸다")
    void putsToAccountPasswordPath() {
        clientReturning(HttpStatus.OK).apply("honggildong", HASH);

        assertThat(sent).hasSize(1);
        assertThat(sent.get(0).method()).isEqualTo(HttpMethod.PUT);
        assertThat(sent.get(0).url().getPath()).isEqualTo("/users/honggildong/password");
    }

    @Test
    @DisplayName("본문은 config-server 필드 이름(passwd_hash)을 쓴다")
    void bodyUsesConfigServerFieldName() throws Exception {
        String json = new ObjectMapper().writeValueAsString(new UbuntuPasswordSyncClient.ChangePasswordRequest(HASH));
        assertThat(json).isEqualTo("{\"passwd_hash\":\"" + HASH + "\"}");
    }

    @Test
    @DisplayName("5xx는 UBUNTU_PASSWORD_CHANGE_FAILED, 4xx는 INFRA_REQUEST_REJECTED")
    void mapsErrors() {
        assertThatThrownBy(() -> clientReturning(HttpStatus.INTERNAL_SERVER_ERROR).apply("honggildong", HASH))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.UBUNTU_PASSWORD_CHANGE_FAILED);
        assertThatThrownBy(() -> clientReturning(HttpStatus.NOT_FOUND).apply("honggildong", HASH))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INFRA_REQUEST_REJECTED);
    }
}
