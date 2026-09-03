package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import DGU_AI_LAB.admin_be.global.webclient.WebClientErrorHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

/**
 * Ubuntu 서버 계정 관리 서비스
 * WebClient를 사용해 인프라 서버의 Ubuntu 계정을 제어합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UbuntuAccountService {

    private final @Qualifier("configWebClient") WebClient webClient;

    public void deleteUbuntuAccount(String username) {
        deleteUbuntuAccount(username, null);
    }

    /**
     * @param nodeName 이 계정의 keytab이 실제로 배포된(또는 배포를 시도한) farm 노드.
     *                 알고 있으면 반드시 넘겨야 한다 — 안 넘기면 config-server가 설정된
     *                 모든 farm 노드를 무차별로 훑어서, 같은 유저네임을 쓰는 무관한
     *                 레거시 계정까지 잘못 지울 수 있다.
     */
    public void deleteUbuntuAccount(String username, String nodeName) {

        try {
            log.info("사용자 삭제 API 호출 시작: {}, node={}", username, nodeName);
            String uri = "/accounts/users/" + username;
            if (nodeName != null && !nodeName.isBlank()) {
                uri += "?node_name=" + nodeName;
            }
            WebClientErrorHandler.onError(
                            webClient.delete()
                                    .uri(uri)
                                    .retrieve(),
                            (status, body) -> {
                                if (status == HttpStatus.NOT_FOUND) {
                                    log.warn("사용자가 이미 존재하지 않음 (404): {}", username);
                                    return null;
                                }
                                if (status == HttpStatus.BAD_REQUEST) {
                                    log.error("사용자 삭제 실패 (400 Bad Request): {}", body);
                                    return new BusinessException("사용자 삭제 요청 오류: " + body, ErrorCode.UBUNTU_USER_DELETION_FAILED);
                                }
                                log.error("사용자 삭제 실패 ({}): {}", status, body);
                                return new BusinessException("사용자 삭제 실패: " + body, ErrorCode.UBUNTU_USER_DELETION_FAILED);
                            }
                    )
                    .bodyToMono(Map.class)
                    .block();
            log.info("사용자 삭제 성공: {}", username);
        } catch (Exception e) {
            log.error("사용자 삭제 API 호출 중 예기치 않은 오류: {}", username, e);
            throw new BusinessException("사용자 삭제 API 호출 오류", ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
}
