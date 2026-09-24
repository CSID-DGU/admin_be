package DGU_AI_LAB.admin_be.domain.users.service;

import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import DGU_AI_LAB.admin_be.global.webclient.WebClientErrorHandler;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;

/**
 * config-server에 계정 로그인 비밀번호 교체를 요청한다(PUT /accounts/users/{username}/password).
 * config-server가 계정 원장·모든 계정 Secret·떠 있는 Pod를 같은 해시로 바꾸고, 하나라도 실패하면
 * 5xx로 답한다. 멱등이라 실패하면 같은 요청을 다시 보내면 된다.
 */
@Slf4j
@Component
public class UbuntuPasswordSyncClient {

    private final WebClient configWebClient;

    public UbuntuPasswordSyncClient(@Qualifier("configWebClient") WebClient configWebClient) {
        this.configWebClient = configWebClient;
    }

    public void apply(String username, String passwordHash) {
        log.info("[UbuntuPasswordSync] 비밀번호 교체 요청: username={}", username);
        try {
            WebClientErrorHandler.onError(
                            configWebClient
                                    .put()
                                    .uri("/users/{username}/password", username)
                                    .bodyValue(new ChangePasswordRequest(passwordHash))
                                    .retrieve(),
                            (status, body) -> WebClientErrorHandler.rejectedOr(status, body,
                                    "우분투 비밀번호 교체 실패", ErrorCode.UBUNTU_PASSWORD_CHANGE_FAILED)
                    )
                    .toBodilessEntity()
                    .block();
        } catch (WebClientRequestException e) {
            throw new BusinessException("config-server에 연결하지 못함: " + e.getMessage(),
                    ErrorCode.UBUNTU_PASSWORD_CHANGE_FAILED);
        }
        log.info("[UbuntuPasswordSync] 비밀번호 교체 완료: username={}", username);
    }

    record ChangePasswordRequest(@JsonProperty("passwd_hash") String passwdHash) {
    }
}
