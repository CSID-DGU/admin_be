package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
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
 * 신청에 딸리지 않은 고아 Pod를 지우는 Infra API 호출 서비스.
 * 신청에 딸린 컨테이너의 생성·회수는 config-server 작업(JobClient)이 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PodService {

    private final @Qualifier("podWebClient") WebClient webClient;
    private final RequestRepository requestRepository;

    /**
     * DB에 대응하는 Request가 전혀 없는 고아 Pod를 관리자가 k8s에서 직접 지운다.
     * config-server가 pod가 뜬 경로를 우회해서(예: 자동화 시스템을 거치지 않은 수동 생성)
     * DB에 흔적이 안 남는 경우가 실제로 있었다 — 그런 Pod는 User/Request 삭제 흐름으로는
     * 절대 안 지워지므로 이 경로가 유일한 정리 수단이다. 신청 이력이 있는 Pod는 계정 삭제/
     * NodePort 정리 등 딸린 정리 작업이 있으므로 이 경로로 지우지 못하게 막는다 — 그런
     * Pod는 사용자 삭제/신청 만료 같은 정식 경로를 거쳐야 한다.
     */
    public void deleteOrphanPod(String podName) {
        if (requestRepository.existsByPodName(podName)) {
            throw new BusinessException(ErrorCode.POD_NOT_ORPHAN);
        }
        try {
            log.info("고아 Pod 삭제 API 요청 시작: {}", podName);

            WebClientErrorHandler.onError(
                            webClient.delete()
                                    .uri("/pods/{podName}", podName)
                                    .retrieve(),
                            (status, body) -> {
                                if (status == HttpStatus.NOT_FOUND) {
                                    log.warn("Pod가 이미 존재하지 않음 (404): {}", podName);
                                    return null;
                                }
                                log.error("Pod 삭제 실패 ({}): {}", status, body);
                                return WebClientErrorHandler.rejectedOr(status, body, "Pod 삭제 실패", ErrorCode.POD_DELETION_FAILED);
                            }
                    )
                    .bodyToMono(Map.class)
                    .block();

            log.info("고아 Pod 삭제 API 요청 성공: {}", podName);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("고아 Pod 삭제 API 호출 중 예기치 않은 오류: {}", podName, e);
            throw new BusinessException("Pod 삭제 API 호출 오류", ErrorCode.POD_DELETION_FAILED);
        }
    }

}
