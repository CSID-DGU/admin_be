package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.domain.requests.dto.request.ProvisionRegisterRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.RevokeRegisterRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.JobResultResponseDTO;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import DGU_AI_LAB.admin_be.global.webclient.WebClientErrorHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

/**
 * 제안 시스템(v2.0)의 작업 등록 경로. 승인·회수를 config-server에 작업으로 등록만 하고 바로 돌아오며,
 * 실제 실행은 config-server의 제어기가 한다. 결과는 작업 결과 조회로 받는다.
 *
 * <p>Operational Baseline(동기식 순차 실행)은 이 경로를 쓰지 않는다. 승인 API가 계정 생성부터 컨테이너
 * 생성까지 직접 호출하고 끝까지 기다리는 흐름은 그대로 두고, {@code proposed.async-approval.enabled}가
 * 켜진 실험 스택에서만 이 경로로 전환된다.
 */
@Slf4j
@Service
public class OperationJobService {

    /** 등록 이력이 없다. 아직 등록 전이거나 동기 경로로 처리 중인 신청이다. */
    public static final String PHASE_NONE = "none";
    /** 등록됐고 아직 끝나지 않았다(대기 또는 실행 중). */
    public static final String PHASE_START = "START";
    public static final String PHASE_SUCCESS = "SUCCESS";
    public static final String PHASE_FAIL = "FAIL";
    /**
     * 결과 불명. 실행 여부 자체를 알 수 없어(타임아웃 등) 실패와 구분해 기록된 상태다.
     * 자원이 남아 있을 수 있으므로 실패처럼 자동으로 되돌리면 안 된다.
     */
    public static final String PHASE_UNKNOWN = "UNKNOWN";

    public static final String KIND_PROVISION = "provision";
    public static final String KIND_REVOKE = "revoke";

    private final WebClient webClient;

    public OperationJobService(@Qualifier("configWebClient") WebClient configWebClient) {
        this.webClient = configWebClient;
    }

    /**
     * 생성 작업을 등록한다. 등록만 하고 돌아오므로 계정·컨테이너가 아직 만들어지지 않은 상태에서 반환된다.
     *
     * @throws BusinessException 같은 신청의 생성 작업이 아직 끝나지 않았거나(409) 등록 자체가 실패한 경우
     */
    public void registerProvision(ProvisionRegisterRequestDTO body) {
        register("/operations/provision", body, body.requestId(), ErrorCode.POD_CREATION_FAILED);
    }

    /** 회수 작업을 등록한다. */
    public void registerRevoke(RevokeRegisterRequestDTO body) {
        register("/operations/revoke", body, body.requestId(), ErrorCode.POD_DELETION_FAILED);
    }

    private void register(String uri, Object body, Long requestId, ErrorCode failureCode) {
        try {
            log.info("작업 등록 요청: {}, requestId: {}", uri, requestId);
            WebClientErrorHandler.onError(
                            webClient.post()
                                    .uri(uri)
                                    .bodyValue(body)
                                    .retrieve(),
                            (status, responseBody) -> {
                                // 409는 같은 신청의 작업이 아직 끝나지 않았다는 뜻이다. 다시 등록하면
                                // 같은 신청에 컨테이너가 두 개 생길 수 있으므로 재시도하지 않는다.
                                if (status == HttpStatus.CONFLICT) {
                                    return new BusinessException(
                                            "이미 처리 중인 작업이 있습니다: " + responseBody,
                                            ErrorCode.INVALID_REQUEST_STATUS);
                                }
                                return new BusinessException("작업 등록 실패: " + responseBody, failureCode);
                            })
                    .bodyToMono(Map.class)
                    .block();
            log.info("작업 등록 완료: {}, requestId: {}", uri, requestId);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("작업 등록 중 예기치 않은 오류: {}, requestId: {}", uri, requestId, e);
            throw new BusinessException(failureCode);
        }
    }

    /**
     * 작업 결과를 조회한다. 등록 이력이 없으면 phase가 {@link #PHASE_NONE}으로 온다.
     *
     * @param kind {@link #KIND_PROVISION} 또는 {@link #KIND_REVOKE}
     */
    public JobResultResponseDTO getResult(String kind, Long requestId) {
        try {
            JobResultResponseDTO response = WebClientErrorHandler.onError(
                            webClient.get()
                                    .uri("/operations/" + kind + "/" + requestId)
                                    .retrieve(),
                            (status, body) -> new BusinessException("작업 결과 조회 실패: " + body,
                                    ErrorCode.EXTERNAL_API_ERROR))
                    .bodyToMono(JobResultResponseDTO.class)
                    .block();

            if (response == null || response.phase() == null) {
                log.error("작업 결과 조회 API가 빈 응답을 반환했습니다. kind: {}, requestId: {}", kind, requestId);
                throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR);
            }
            return response;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("작업 결과 조회 중 예기치 않은 오류. kind: {}, requestId: {}", kind, requestId, e);
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR);
        }
    }
}
