package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.domain.requests.dto.request.RevokeRegisterRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.MigratePodResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import DGU_AI_LAB.admin_be.global.webclient.WebClientErrorHandler;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

/**
 * Pod 회수·마이그레이션·생성 진행 상황 관련 Infra API 호출 서비스.
 * Pod 생성은 승인 때 등록하는 생성 작업(OperationJobService)이 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PodService {

    private final @Qualifier("podWebClient") WebClient webClient;
    private final @Qualifier("configWebClient") WebClient configWebClient;
    private final RequestRepository requestRepository;
    private final OperationJobService operationJobService;

    // 대응하는 신청이 없는 고아 Pod 정리 전용. 작업 인터페이스는 신청 번호로만 작업을 식별하므로
    // 이 경우만 config-server의 Pod 삭제 API를 직접 부른다.
    private record DeleteOrphanPodRequest(
            @JsonProperty("pod_name") String podName
    ) {}

    // config-server는 min_improvement_ratio 키가 아예 없어야 자체 기본값(0.2)을 쓴다.
    // null을 그대로 보내면 data.get(key, default)가 "키는 있지만 값이 None"이라 default가
    // 적용되지 않고 그대로 None을 반환해 마이그레이션이 500으로 실패한다.
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record MigratePodRequest(
            String username,
            // 한 사용자가 Pod를 여러 개 동시에 가질 수 있어, username만으로는 config-server가
            // "그 유저의 Pod"를 하나로 특정 못 한다. 정확한 대상을 짚어주기 위해 함께 보낸다.
            @JsonProperty("pod_name") String podName,
            @JsonProperty("request_id") Long requestId,
            List<String> nodes,
            @JsonProperty("min_improvement_ratio") Double minImprovementRatio,
            // true면 config-server가 개선 비율을 보지 않고 이전한다.
            Boolean force
    ) {}

    /**
     * 신청에 딸린 컨테이너를 회수한다. config-server에 회수 작업을 등록하고 끝날 때까지 기다린다 —
     * 호출자(만료 정리·사용자 정리)는 이 메서드가 정상 반환하면 컨테이너가 사라진 것으로 보고 신청을 정리한다.
     * 이미 없는 Pod도 성공으로 끝난다.
     *
     * @param requestId 이 회수를 유발한 신청 PK. 작업은 신청 번호로 식별되므로 반드시 넘긴다.
     *                  대응하는 신청이 없는 고아 Pod는 {@link #deleteOrphanPod}를 쓴다.
     */
    public void deletePod(String podName, Long requestId) {
        if (podName == null) {
            log.warn("pod_name이 없어 Pod 삭제를 건너뜁니다.");
            return;
        }
        if (requestId == null) {
            throw new BusinessException("신청 번호 없이 컨테이너를 회수할 수 없습니다: " + podName, ErrorCode.POD_DELETION_FAILED);
        }
        log.info("컨테이너 회수 작업 요청: {}, requestId: {}", podName, requestId);
        operationJobService.revokeAndWait(
                new RevokeRegisterRequestDTO(requestId, podName, null, null, false), ErrorCode.POD_DELETION_FAILED);
        log.info("컨테이너 회수 완료: {}, requestId: {}", podName, requestId);
    }

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
                            webClient.post()
                                    .uri("/delete-pod")
                                    .bodyValue(new DeleteOrphanPodRequest(podName))
                                    .retrieve(),
                            (status, body) -> {
                                if (status == HttpStatus.NOT_FOUND) {
                                    log.warn("Pod가 이미 존재하지 않음 (404): {}", podName);
                                    return null;
                                }
                                log.error("Pod 삭제 실패 ({}): {}", status, body);
                                return new BusinessException("Pod 삭제 실패: " + body, ErrorCode.POD_DELETION_FAILED);
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

    public MigratePodResponseDTO migratePod(String username, String podName, Long requestId, List<String> nodes, Double minImprovementRatio, Boolean force) {
        try {
            log.info("Pod 마이그레이션 API 요청 시작: 사용자: {}, pod: {}, requestId: {}, 후보 노드: {}", username, podName, requestId, nodes);

            MigratePodResponseDTO response = WebClientErrorHandler.onError(
                            webClient.post()
                                    .uri("/migrate")
                                    .bodyValue(new MigratePodRequest(username, podName, requestId, nodes, minImprovementRatio, force))
                                    .retrieve(),
                            (status, body) -> new BusinessException("Pod 마이그레이션 실패: " + body, ErrorCode.POD_MIGRATION_FAILED)
                    )
                    .bodyToMono(MigratePodResponseDTO.class)
                    .block();

            if (response == null || response.status() == null) {
                log.error("Pod 마이그레이션 API가 빈 응답을 반환했습니다. 사용자: {}", username);
                throw new BusinessException(ErrorCode.POD_MIGRATION_FAILED);
            }
            log.info("Pod 마이그레이션 API 요청 성공: 사용자: {}, 결과: {}", username, response.status());
            return response;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Pod 마이그레이션 API 호출 중 예기치 않은 오류 발생.", e);
            throw new BusinessException(ErrorCode.POD_MIGRATION_FAILED);
        }
    }
}
