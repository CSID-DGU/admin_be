package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

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

        try {
            log.info("사용자 삭제 API 호출 시작: {}", username);
            webClient.delete()
                    .uri("/accounts/users/" + username)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, response ->
                            response.bodyToMono(String.class)
                                    .flatMap(body -> {
                                        if (response.statusCode() == HttpStatus.NOT_FOUND) {
                                            log.warn("사용자가 이미 존재하지 않음 (404): {}", username);
                                            return Mono.empty();
                                        }
                                        if (response.statusCode() == HttpStatus.BAD_REQUEST) {
                                            log.error("사용자 삭제 실패 (400 Bad Request): {}", body);
                                            return Mono.error(new BusinessException("사용자 삭제 요청 오류: " + body, ErrorCode.UBUNTU_USER_DELETION_FAILED));
                                        }
                                        log.error("사용자 삭제 실패 ({}): {}", response.statusCode(), body);
                                        return Mono.error(new BusinessException("사용자 삭제 실패: " + body, ErrorCode.UBUNTU_USER_DELETION_FAILED));
                                    })
                    )
                    .bodyToMono(Map.class)
                    .block();
            log.info("사용자 삭제 성공: {}", username);
        } catch (Exception e) {
            if (!(e instanceof WebClientResponseException && ((WebClientResponseException)e).getStatusCode() == HttpStatus.NOT_FOUND)) {
                log.error("사용자 삭제 API 호출 중 예기치 않은 오류: {}", username, e);
                throw new BusinessException("사용자 삭제 API 호출 오류", ErrorCode.INTERNAL_SERVER_ERROR);
            }
        }
    }
}
