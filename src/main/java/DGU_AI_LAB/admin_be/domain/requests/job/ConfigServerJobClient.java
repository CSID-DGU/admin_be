package DGU_AI_LAB.admin_be.domain.requests.job;

import DGU_AI_LAB.admin_be.domain.requests.dto.request.MigrateRegisterRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.ProvisionRegisterRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.RevokeRegisterRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.JobResultResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.JobStepsResponseDTO;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import DGU_AI_LAB.admin_be.global.webclient.WebClientErrorHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

/**
 * config-server의 작업 API(POST /operations/{provision|migrate|revoke}, GET /operations/{kind}/{신청번호}[/steps])로
 * {@link JobClient}를 구현한다.
 */
@Slf4j
@Component
public class ConfigServerJobClient implements JobClient {

    private final WebClient webClient;

    public ConfigServerJobClient(@Qualifier("configWebClient") WebClient configWebClient) {
        this.webClient = configWebClient;
    }

    @Override
    public Long registerProvision(ProvisionRegisterRequestDTO body) {
        return register("/operations/provision", body, body.requestId(), ErrorCode.POD_CREATION_FAILED);
    }

    @Override
    public Long registerMigrate(MigrateRegisterRequestDTO body) {
        return register("/operations/migrate", body, body.requestId(), ErrorCode.POD_MIGRATION_FAILED);
    }

    @Override
    public Long registerRevoke(RevokeRegisterRequestDTO body, ErrorCode failureCode) {
        return register("/operations/revoke", body, body.requestId(), failureCode);
    }

    private Long register(String uri, Object body, Long requestId, ErrorCode failureCode) {
        Map<?, ?> response;
        try {
            log.info("작업 등록 요청: {}, requestId: {}", uri, requestId);
            response = WebClientErrorHandler.onError(
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
                                return WebClientErrorHandler.rejectedOr(status, responseBody, "작업 등록 실패", failureCode);
                            })
                    .bodyToMono(Map.class)
                    .block();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("작업 등록 중 예기치 않은 오류: {}, requestId: {}", uri, requestId, e);
            throw new BusinessException("작업 등록 중 오류: " + e.getMessage(), failureCode, e);
        }
        Long jobId = response != null && response.get("job_id") instanceof Number n ? n.longValue() : null;
        log.info("작업 등록 완료: {}, requestId: {}, jobId: {}", uri, requestId, jobId);
        return jobId;
    }

    @Override
    public JobStepsResponseDTO getSteps(String kind, Long requestId) {
        try {
            JobStepsResponseDTO response = WebClientErrorHandler.onError(
                            webClient.get()
                                    .uri("/operations/" + kind + "/" + requestId + "/steps")
                                    .retrieve(),
                            (status, body) -> new BusinessException("작업 단계 기록 조회 실패: " + body,
                                    ErrorCode.EXTERNAL_API_ERROR))
                    .bodyToMono(JobStepsResponseDTO.class)
                    .block();

            if (response == null || response.jobs() == null) {
                log.error("작업 단계 기록 조회 API가 빈 응답을 반환했습니다. kind: {}, requestId: {}", kind, requestId);
                throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR);
            }
            return response;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("작업 단계 기록 조회 중 예기치 않은 오류. kind: {}, requestId: {}", kind, requestId, e);
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR);
        }
    }

    @Override
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
