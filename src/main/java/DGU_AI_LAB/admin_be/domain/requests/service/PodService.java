package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.domain.requests.dto.request.CreatePodRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.CreatePodResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.MigratePodResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.PodCreationStatusResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import DGU_AI_LAB.admin_be.global.webclient.WebClientErrorHandler;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

/**
 * Pod 생성/삭제 관련 Infra API 호출 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PodService {

    private final @Qualifier("podWebClient") WebClient webClient;
    private final @Qualifier("configWebClient") WebClient configWebClient;
    private final RequestRepository requestRepository;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * config-server 에러 응답 바디(JSON 문자열)에서 "node" 필드만 뽑아낸다. 실패 응답에
     * 어느 farm에 배포를 시도했는지가 담겨있는데, 그래야 계정 삭제 보상 트랜잭션이 그
     * 노드로만 정리를 좁힐 수 있다. 파싱 실패나 필드 부재는 흔한 경우(모든 에러 응답에
     * node가 있는 건 아님)이므로 조용히 null을 반환한다.
     */
    private static String extractNode(String body) {
        try {
            JsonNode json = OBJECT_MAPPER.readTree(body);
            JsonNode node = json.get("node");
            return (node != null && !node.isNull()) ? node.asText() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private record DeletePodRequest(@com.fasterxml.jackson.annotation.JsonProperty("pod_name") String podName) {}

    // config-server는 min_improvement_ratio 키가 아예 없어야 자체 기본값(0.2)을 쓴다.
    // null을 그대로 보내면 data.get(key, default)가 "키는 있지만 값이 None"이라 default가
    // 적용되지 않고 그대로 None을 반환해 마이그레이션이 500으로 실패한다.
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record MigratePodRequest(
            String username,
            List<String> nodes,
            @JsonProperty("min_improvement_ratio") Double minImprovementRatio
    ) {}

    public CreatePodResponseDTO createPod(String username, Long requestId) {
        try {
            log.info("Pod 생성 API 요청 시작: 사용자: {}, requestId: {}", username, requestId);

            CreatePodResponseDTO response = WebClientErrorHandler.onError(
                            webClient.post()
                                    .uri("/create-pod")
                                    .bodyValue(new CreatePodRequestDTO(username, requestId))
                                    .retrieve(),
                            (status, body) -> new PodCreationFailedException("Pod 생성 실패: " + body, ErrorCode.POD_CREATION_FAILED, extractNode(body))
                    )
                    .bodyToMono(CreatePodResponseDTO.class)
                    .block();

            if (response == null || response.podName() == null) {
                log.error("Pod 생성 API가 빈 응답을 반환했습니다. 사용자: {}", username);
                throw new BusinessException(ErrorCode.POD_CREATION_FAILED);
            }
            log.info("Pod 생성 API 요청 성공: 사용자: {}, pod: {}", username, response.podName());
            return response;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Pod 생성 API 호출 중 예기치 않은 오류 발생.", e);
            throw new BusinessException(ErrorCode.POD_CREATION_FAILED);
        }
    }

    public void deletePod(String podName) {
        if (podName == null) {
            log.warn("pod_name이 없어 Pod 삭제를 건너뜁니다.");
            return;
        }

        try {
            log.info("Pod 삭제 API 요청 시작: {}", podName);

            WebClientErrorHandler.onError(
                            webClient.post()
                                    .uri("/delete-pod")
                                    .bodyValue(new DeletePodRequest(podName))
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

            log.info("Pod 삭제 API 요청 성공: {}", podName);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Pod 삭제 API 호출 중 예기치 않은 오류: {}", podName, e);
            throw new BusinessException("Pod 삭제 API 호출 오류", ErrorCode.POD_DELETION_FAILED);
        }
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
        deletePod(podName);
    }

    public MigratePodResponseDTO migratePod(String username, List<String> nodes, Double minImprovementRatio) {
        try {
            log.info("Pod 마이그레이션 API 요청 시작: 사용자: {}, 후보 노드: {}", username, nodes);

            MigratePodResponseDTO response = WebClientErrorHandler.onError(
                            webClient.post()
                                    .uri("/migrate")
                                    .bodyValue(new MigratePodRequest(username, nodes, minImprovementRatio))
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

    public PodCreationStatusResponseDTO getPodCreationStatus(Long requestId) {
        try {
            PodCreationStatusResponseDTO response = WebClientErrorHandler.onError(
                            configWebClient.get()
                                    .uri("/requests/" + requestId + "/status")
                                    .retrieve(),
                            (status, body) -> new BusinessException("Pod 생성 상태 조회 실패: " + body, ErrorCode.EXTERNAL_API_ERROR)
                    )
                    .bodyToMono(PodCreationStatusResponseDTO.class)
                    .block();

            if (response == null) {
                log.error("Pod 생성 상태 조회 API가 빈 응답을 반환했습니다. requestId: {}", requestId);
                throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR);
            }
            return response;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Pod 생성 상태 조회 API 호출 중 예기치 않은 오류 발생. requestId: {}", requestId, e);
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR);
        }
    }
}
