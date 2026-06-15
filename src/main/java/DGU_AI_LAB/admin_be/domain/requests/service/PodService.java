package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.domain.requests.dto.request.CreatePodRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.CreatePodResponseDTO;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import DGU_AI_LAB.admin_be.error.exception.InfraErrorParser;
import DGU_AI_LAB.admin_be.error.exception.InfraOperationException;
import DGU_AI_LAB.admin_be.error.exception.InfraOperationException.InfraStep;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Pod 생성/삭제 관련 Infra API 호출 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PodService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final @Qualifier("configWebClient") WebClient webClient;

    private record DeletePodRequest(@com.fasterxml.jackson.annotation.JsonProperty("pod_name") String podName) {}

    public CreatePodResponseDTO createPod(String username) {
        try {
            log.info("Pod 생성 API 요청 시작: 사용자: {}", username);

            CreatePodResponseDTO response = webClient.post()
                    .uri("/create-pod")
                    .bodyValue(new CreatePodRequestDTO(username))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, clientResponse ->
                            clientResponse.bodyToMono(String.class)
                                    .flatMap(body -> {
                                        InfraErrorParser.ParsedInfraError parsed = InfraErrorParser.parse(OBJECT_MAPPER, body);
                                        String infraError = parsed != null ? parsed.error() : null;
                                        ErrorCode errorCode = resolveCreatePodErrorCode(infraError, clientResponse.statusCode().value());
                                        return Mono.error(InfraErrorParser.toException(
                                                OBJECT_MAPPER, errorCode, InfraStep.CREATE_POD,
                                                clientResponse.statusCode().value(),
                                                "Pod 생성 실패", "HTTP_ERROR", body
                                        ));
                                    })
                    )
                    .bodyToMono(CreatePodResponseDTO.class)
                    .block();

            if (response == null) {
                throw new InfraOperationException(
                        ErrorCode.POD_CREATION_FAILED,
                        "Pod 생성 실패: infra 응답이 비어 있습니다.",
                        InfraStep.CREATE_POD, 200,
                        "EMPTY_RESPONSE", "infra 응답이 비어 있습니다.",
                        null, null, null, null
                );
            }

            log.info("Pod 생성 API 요청 성공: 사용자: {}, pod: {}", username, response.podName());
            return response;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Pod 생성 API 호출 중 예기치 않은 오류 발생.", e);
            throw new InfraOperationException(
                    ErrorCode.POD_CREATION_FAILED,
                    "Pod 생성 API 호출 중 예기치 않은 오류 발생.",
                    InfraStep.CREATE_POD, null,
                    "UNEXPECTED_ERROR", e.getMessage(),
                    null, null, null, null
            );
        }
    }

    private ErrorCode resolveCreatePodErrorCode(String infraError, int httpStatus) {
        if (infraError == null) return ErrorCode.POD_CREATION_FAILED;
        return switch (infraError) {
            case "POD_ALREADY_EXISTS"    -> ErrorCode.POD_ALREADY_EXISTS;
            case "NODE_SELECTION_FAILED" -> ErrorCode.NODE_SELECTION_FAILED;
            case "USER_CONFIG_NOT_FOUND" -> ErrorCode.USER_CONFIG_NOT_FOUND;
            case "POD_READY_TIMEOUT"     -> ErrorCode.POD_READY_TIMEOUT;
            default                      -> ErrorCode.POD_CREATION_FAILED;
        };
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void deletePod(String podName) {
        if (podName == null) {
            log.warn("pod_name이 없어 Pod 삭제를 건너뜁니다.");
            return;
        }

        try {
            log.info("Pod 삭제 API 요청 시작: {}", podName);

            @SuppressWarnings("unchecked")
            Map<String, Object> result = webClient.post()
                    .uri("/delete-pod")
                    .bodyValue(new DeletePodRequest(podName))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, response ->
                            response.bodyToMono(String.class)
                                    .flatMap(body -> {
                                        if (response.statusCode() == HttpStatus.NOT_FOUND) {
                                            log.warn("Pod가 이미 존재하지 않음 (404): {}", podName);
                                            return Mono.empty();
                                        }
                                        log.error("Pod 삭제 실패 ({}): {}", response.statusCode(), body);
                                        InfraErrorParser.ParsedInfraError parsed = InfraErrorParser.parse(OBJECT_MAPPER, body);
                                        String infraError = parsed != null ? parsed.error() : null;
                                        ErrorCode errorCode = resolveDeletePodErrorCode(infraError);
                                        return Mono.error(InfraErrorParser.toException(
                                                OBJECT_MAPPER, errorCode, InfraStep.DELETE_POD,
                                                response.statusCode().value(),
                                                "Pod 삭제 실패", "HTTP_ERROR", body
                                        ));
                                    })
                    )
                    .bodyToMono(Map.class)
                    .block();

            if (result != null && Boolean.TRUE.equals(result.get("already_absent"))) {
                log.info("Pod 이미 부재 (already_absent=true): {}", podName);
            }

            log.info("Pod 삭제 API 요청 성공: {}", podName);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Pod 삭제 API 호출 중 예기치 않은 오류: {}", podName, e);
            throw new InfraOperationException(
                    ErrorCode.POD_DELETION_FAILED,
                    "Pod 삭제 API 호출 오류",
                    InfraStep.DELETE_POD, null,
                    "UNEXPECTED_ERROR", e.getMessage(),
                    null, null, null, null
            );
        }
    }

    private ErrorCode resolveDeletePodErrorCode(String infraError) {
        if ("POD_DELETE_TIMEOUT".equals(infraError)) return ErrorCode.POD_DELETE_TIMEOUT;
        return ErrorCode.POD_DELETION_FAILED;
    }
}
