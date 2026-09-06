package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.domain.requests.dto.request.CreatePodRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.CreatePodResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.MigratePodResponseDTO;
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

    public CreatePodResponseDTO createPod(String username) {
        try {
            log.info("Pod 생성 API 요청 시작: 사용자: {}", username);

            CreatePodResponseDTO response = WebClientErrorHandler.onError(
                            webClient.post()
                                    .uri("/create-pod")
                                    .bodyValue(new CreatePodRequestDTO(username))
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
}
