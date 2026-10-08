package DGU_AI_LAB.admin_be.domain.requests.job;

import DGU_AI_LAB.admin_be.domain.requests.dto.request.GroupChangeRegisterRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.HomeDeleteRegisterRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.MigrateRegisterRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.PasswordChangeRegisterRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.PortChangeRegisterRequestDTO;
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
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * config-server의 작업 API(POST /operations/{provision|migrate|revoke|password|group|home|port}, GET /operations/{kind}/{신청번호}[/steps])로
 * {@link JobClient}를 구현한다.
 */
@Slf4j
@Component
public class ConfigServerJobClient implements JobClient {

    /**
     * 결과·단계 조회는 config-server DB 한 번 읽기라 금방 끝난다. 공용 클라이언트의 응답 제한(그룹 생성 같은 긴
     * 동기 호출용)을 그대로 쓰면 config-server가 멈춘 동안 폴러가 신청 하나에 그만큼씩 스케줄러 스레드를 쥔다.
     */
    static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    /** 그룹 작업 등록을 거절하는 config-server 오류 코드와, 그것을 화면에 알릴 오류 코드. */
    private static final Map<String, ErrorCode> GROUP_REJECTIONS = Map.of(
            "INVALID_GROUP_MEMBER", ErrorCode.INVALID_GROUP_MEMBER,
            "USER_NOT_FOUND", ErrorCode.INVALID_GROUP_MEMBER,
            "GROUP_NAME_CONFLICTS_USER", ErrorCode.GROUP_NAME_CONFLICTS_USER,
            "GROUP_NAME_RESERVED", ErrorCode.RESERVED_GROUP_NAME,
            "GROUP_NOT_FOUND", ErrorCode.GROUP_NOT_FOUND,
            "PRIMARY_GROUP", ErrorCode.PRIMARY_GROUP_REMOVAL,
            "JOB_ALREADY_REGISTERED", ErrorCode.GROUP_OPERATION_IN_PROGRESS);

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

    @Override
    public Long registerPasswordChange(PasswordChangeRegisterRequestDTO body) {
        return register("/operations/password", body, body.requestId(), ErrorCode.UBUNTU_PASSWORD_CHANGE_FAILED);
    }

    @Override
    public Long registerGroupChange(GroupChangeRegisterRequestDTO body) {
        return register("/operations/group", body, body.requestId(), ConfigServerJobClient::groupRegistrationError,
                ErrorCode.GROUP_CHANGE_FAILED);
    }

    @Override
    public Long registerHomeDelete(HomeDeleteRegisterRequestDTO body) {
        return register("/operations/home", body, body.requestId(), ErrorCode.HOME_DELETE_FAILED);
    }

    @Override
    public Long registerPortChange(PortChangeRegisterRequestDTO body) {
        return register("/operations/port", body, body.requestId(), ErrorCode.PORT_CHANGE_FAILED);
    }

    /**
     * 그룹 작업 등록이 거절된 이유를 본문의 error 필드로 가른다. 사람이 읽는 문장이 아니라 기계 코드라 문구가
     * 바뀌어도 깨지지 않는다. 하나로 묶으면 "이름을 바꿔야 하는 것"과 "다시 누르면 되는 것"을 구분할 수 없다.
     * 시험이 닿도록 패키지 범위로 둔다.
     */
    static BusinessException groupRegistrationError(HttpStatusCode status, String responseBody) {
        String body = responseBody == null ? "" : responseBody;
        for (Map.Entry<String, ErrorCode> rejection : GROUP_REJECTIONS.entrySet()) {
            if (body.contains("\"" + rejection.getKey() + "\"")) {
                return new BusinessException("그룹 작업 등록 거절: " + body, rejection.getValue());
            }
        }
        return WebClientErrorHandler.rejectedOr(status, body, "그룹 작업 등록 실패", ErrorCode.GROUP_CHANGE_FAILED);
    }

    private Long register(String uri, Object body, Long requestId, ErrorCode failureCode) {
        return register(uri, body, requestId, (status, responseBody) -> {
            // 409는 같은 신청의 작업이 아직 끝나지 않았다는 뜻이다. 다시 등록하면
            // 같은 신청에 컨테이너가 두 개 생길 수 있으므로 재시도하지 않는다.
            if (status == HttpStatus.CONFLICT) {
                return new BusinessException("이미 처리 중인 작업이 있습니다: " + responseBody,
                        ErrorCode.INVALID_REQUEST_STATUS);
            }
            return WebClientErrorHandler.rejectedOr(status, responseBody, "작업 등록 실패", failureCode);
        }, failureCode);
    }

    private Long register(String uri, Object body, Long requestId,
                          BiFunction<HttpStatusCode, String, RuntimeException> rejection, ErrorCode failureCode) {
        Map<?, ?> response;
        try {
            log.info("작업 등록 요청: {}, requestId: {}", uri, requestId);
            response = WebClientErrorHandler.onError(
                            webClient.post()
                                    .uri(uri)
                                    .bodyValue(body)
                                    .retrieve(),
                            rejection)
                    .bodyToMono(Map.class)
                    .block();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("작업 등록 중 예기치 않은 오류: {}, requestId: {}", uri, requestId, e);
            if (JobResults.neverReachedServer(e)) {
                throw new BusinessException("작업 등록 중 오류: " + e.getMessage(), failureCode, e);
            }
            throw new JobRegistrationUnconfirmedException("작업 등록 결과를 확인하지 못함: " + e.getMessage(), failureCode, e);
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
                    .timeout(READ_TIMEOUT)
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
                    .timeout(READ_TIMEOUT)
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
