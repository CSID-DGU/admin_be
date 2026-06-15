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
                    .onStatus(HttpStatusCode::is4xxClientError, clientResponse ->
                            clientResponse.bodyToMono(String.class)
                                    .flatMap(body -> Mono.error(toInfraOperationException(
                                            ErrorCode.POD_CREATION_FAILED,
                                            InfraStep.CREATE_POD,
                                            clientResponse.statusCode().value(),
                                            "Pod 생성 실패",
                                            "HTTP_ERROR",
                                            body
                                    )))
                    )
                    .onStatus(HttpStatusCode::is5xxServerError, clientResponse ->
                            clientResponse.bodyToMono(String.class)
                                    .flatMap(body -> Mono.error(toInfraOperationException(
                                            ErrorCode.POD_CREATION_FAILED,
                                            InfraStep.CREATE_POD,
                                            clientResponse.statusCode().value(),
                                            "Pod 생성 실패",
                                            "HTTP_ERROR",
                                            body
                                    )))
                    )
                    .bodyToMono(CreatePodResponseDTO.class)
                    .block();

            if (response == null) {
                throw new InfraOperationException(
                        ErrorCode.POD_CREATION_FAILED,
                        "Pod 생성 실패: infra 응답이 비어 있습니다.",
                        InfraStep.CREATE_POD,
                        200,
                        "EMPTY_RESPONSE",
                        "infra 응답이 비어 있습니다.",
                        null,
                        null
                );
            }

            log.info("Pod 생성 API 요청 성공: 사용자: {}, pod: {}", username, response != null ? response.podName() : "null");
            return response;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Pod 생성 API 호출 중 예기치 않은 오류 발생.", e);
            throw new InfraOperationException(
                    ErrorCode.POD_CREATION_FAILED,
                    "Pod 생성 API 호출 중 예기치 않은 오류 발생.",
                    InfraStep.CREATE_POD,
                    null,
                    "UNEXPECTED_ERROR",
                    e.getMessage(),
                    null,
                    null
            );
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void deletePod(String podName) {
        if (podName == null) {
            log.warn("pod_name이 없어 Pod 삭제를 건너뜁니다.");
            return;
        }

        try {
            log.info("Pod 삭제 API 요청 시작: {}", podName);

            webClient.post()
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
                                        return Mono.error(toInfraOperationException(
                                                ErrorCode.POD_DELETION_FAILED,
                                                InfraStep.DELETE_POD,
                                                response.statusCode().value(),
                                                "Pod 삭제 실패",
                                                "HTTP_ERROR",
                                                body
                                        ));
                                    })
                    )
                    .bodyToMono(Map.class)
                    .block();

            log.info("Pod 삭제 API 요청 성공: {}", podName);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Pod 삭제 API 호출 중 예기치 않은 오류: {}", podName, e);
            throw new InfraOperationException(
                    ErrorCode.POD_DELETION_FAILED,
                    "Pod 삭제 API 호출 오류",
                    InfraStep.DELETE_POD,
                    null,
                    "UNEXPECTED_ERROR",
                    e.getMessage(),
                    null,
                    null
            );
        }
    }

    private InfraOperationException toInfraOperationException(
            ErrorCode errorCode,
            InfraStep fallbackStep,
            Integer infraStatus,
            String fallbackMessage,
            String fallbackInfraError,
            String body
    ) {
        InfraErrorParser.ParsedInfraError infraErrorResponse = InfraErrorParser.parse(OBJECT_MAPPER, body);
        InfraStep step = parseInfraStep(infraErrorResponse != null ? infraErrorResponse.step() : null, fallbackStep);
        String message = firstNonBlank(infraErrorResponse != null ? infraErrorResponse.error() : null, fallbackMessage);
        String detail = firstNonBlank(infraErrorResponse != null ? infraErrorResponse.detail() : null, body);
        String infraError = firstNonBlank(infraErrorResponse != null ? infraErrorResponse.error() : null, fallbackInfraError);
        String process = infraErrorResponse != null ? infraErrorResponse.process() : null;

        if (infraErrorResponse == null && body != null && !body.isBlank()) {
            message = fallbackMessage + ": " + body;
        }

        return new InfraOperationException(errorCode, message, step, infraStatus, infraError, detail, body, process);
    }

    private InfraStep parseInfraStep(String step, InfraStep fallback) {
        if (step == null || step.isBlank()) {
            return fallback;
        }
        try {
            return InfraStep.valueOf(step.trim().replace('-', '_').toUpperCase());
        } catch (IllegalArgumentException e) {
            return InfraStep.UNKNOWN;
        }
    }

    private String firstNonBlank(String value, String fallback) {
        return value != null && !value.isBlank() ? value : fallback;
    }
}
