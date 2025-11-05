package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class UbuntuAccountService {

    private final @Qualifier("configWebClient") WebClient webClient;

    private record PvcItem(String name, String type) {}
    private record PvcDeleteRequest(List<PvcItem> pvcs) {}

    /**
     * 외부 서버에 우분투 계정 및 PVC 삭제를 요청합니다.
     * 스케줄러의 트랜잭션에 참여합니다. (실패 시 롤백)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteUbuntuAccount(String username) {

        // 1. PVC 삭제 API 호출
        PvcItem pvcItem = new PvcItem(username, "user");
        PvcDeleteRequest pvcRequestBody = new PvcDeleteRequest(List.of(pvcItem));

        try {
            log.info("PVC 삭제 API 요청 시작: {}", pvcRequestBody);
            webClient.method(HttpMethod.DELETE)
                    .uri("/pvc")
                    .bodyValue(pvcRequestBody) // JSON 본문 설정
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, response ->
                            response.bodyToMono(String.class)
                                    .flatMap(body -> {
                                        // 404 (Not Found)는 이미 삭제된 것이므로 무시 (명세서: 경고 로그만 기록)
                                        if (response.statusCode() == HttpStatus.NOT_FOUND) {
                                            log.warn("PVC가 이미 존재하지 않음 (404): {}", username);
                                            return Mono.empty();
                                        }
                                        // 400 (Bad Request) 처리
                                        if (response.statusCode() == HttpStatus.BAD_REQUEST) {
                                            log.error("PVC 삭제 실패 (400 Bad Request): {}", body);
                                            return Mono.error(new BusinessException("PVC 삭제 요청 오류: " + body, ErrorCode.PVC_API_FAILURE));
                                        }
                                        log.error("PVC 삭제 실패 ({}): {}", response.statusCode(), body);
                                        return Mono.error(new BusinessException("PVC 삭제 실패: " + body, ErrorCode.PVC_API_FAILURE));
                                    })
                    )
                    .bodyToMono(Map.class)
                    .block(); // 동기 실행
            log.info("PVC 삭제 API 요청 성공: 사용자: {}", username);

        } catch (Exception e) {
            // 404가 아닌 다른 오류 처리
            if (!(e instanceof WebClientResponseException && ((WebClientResponseException)e).getStatusCode() == HttpStatus.NOT_FOUND)) {
                log.error("PVC 삭제 API 호출 중 예기치 않은 오류: {}", username, e);
                throw new BusinessException("PVC 삭제 API 호출 오류", ErrorCode.PVC_API_FAILURE);
            }
        }

        // 2. 사용자 삭제 API 호출
        try {
            log.info("사용자 삭제 API 호출 시작: {}", username);
            webClient.delete()
                    .uri("/accounts/users/" + username)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, response ->
                            response.bodyToMono(String.class)
                                    .flatMap(body -> {
                                        // 404 (Not Found)는 이미 삭제된 것으로 간주하고 무시
                                        if (response.statusCode() == HttpStatus.NOT_FOUND) {
                                            log.warn("사용자가 이미 존재하지 않음 (404): {}", username);
                                            return Mono.empty();
                                        }
                                        // 400 (Bad Request) 처리
                                        if (response.statusCode() == HttpStatus.BAD_REQUEST) {
                                            log.error("사용자 삭제 실패 (400 Bad Request): {}", body);
                                            // ErrorCode에 USER_DELETION_FAILED 추가 필요
                                            return Mono.error(new BusinessException("사용자 삭제 요청 오류: " + body, ErrorCode.USER_CREATION_FAILED));
                                        }
                                        log.error("사용자 삭제 실패 ({}): {}", response.statusCode(), body);
                                        return Mono.error(new BusinessException("사용자 삭제 실패: " + body, ErrorCode.USER_CREATION_FAILED));
                                    })
                    )
                    .bodyToMono(Map.class)
                    .block();
            log.info("사용자 삭제 성공: {}", username);
        } catch (Exception e) {
            // 404가 아닌 다른 오류 처리
            if (!(e instanceof WebClientResponseException && ((WebClientResponseException)e).getStatusCode() == HttpStatus.NOT_FOUND)) {
                log.error("사용자 삭제 API 호출 중 예기치 않은 오류: {}", username, e);
                throw new BusinessException("사용자 삭제 API 호출 오류", ErrorCode.INTERNAL_SERVER_ERROR);
            }
        }
    }
}