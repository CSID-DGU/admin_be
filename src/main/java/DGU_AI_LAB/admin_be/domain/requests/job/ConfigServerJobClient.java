package DGU_AI_LAB.admin_be.domain.requests.job;

import DGU_AI_LAB.admin_be.domain.requests.dto.request.AccessChangeRegisterRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.GroupChangeRegisterRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.HomeDeleteRegisterRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.MigrateRegisterRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.PasswordChangeRegisterRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.PortChangeRegisterRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.ProvisionRegisterRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.RevokeRegisterRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.JobResultResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.JobStepsResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.ProvisioningStatusResponseDTO;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import DGU_AI_LAB.admin_be.global.cluster.ClusterClientRegistry;
import DGU_AI_LAB.admin_be.global.webclient.WebClientErrorHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * config-server의 작업 API(POST /operations/{provision|migrate|revoke|password|group|home|port|access}, GET /operations/{kind}/{신청번호}[/steps])로
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

    private final ClusterClientRegistry clients;

    public ConfigServerJobClient(ClusterClientRegistry clients) {
        this.clients = clients;
    }

    @Override
    public Long registerProvision(String clusterId, ProvisionRegisterRequestDTO body) {
        return register(clusterId, "/operations/provision", body, body.requestId(), ErrorCode.POD_CREATION_FAILED);
    }

    @Override
    public Long registerMigrate(String clusterId, MigrateRegisterRequestDTO body) {
        return register(clusterId, "/operations/migrate", body, body.requestId(), ErrorCode.POD_MIGRATION_FAILED);
    }

    @Override
    public Long registerRevoke(String clusterId, RevokeRegisterRequestDTO body, ErrorCode failureCode) {
        return register(clusterId, "/operations/revoke", body, body.requestId(), failureCode);
    }

    @Override
    public Long registerPasswordChange(String clusterId, PasswordChangeRegisterRequestDTO body) {
        return register(clusterId, "/operations/password", body, body.requestId(), ErrorCode.UBUNTU_PASSWORD_CHANGE_FAILED);
    }

    @Override
    public Long registerGroupChange(String clusterId, GroupChangeRegisterRequestDTO body) {
        return register(clusterId, "/operations/group", body, body.requestId(), ConfigServerJobClient::groupRegistrationError,
                ErrorCode.GROUP_CHANGE_FAILED);
    }

    @Override
    public Long registerHomeDelete(String clusterId, HomeDeleteRegisterRequestDTO body) {
        return register(clusterId, "/operations/home", body, body.requestId(), ErrorCode.HOME_DELETE_FAILED);
    }

    @Override
    public Long registerPortChange(String clusterId, PortChangeRegisterRequestDTO body) {
        return register(clusterId, "/operations/port", body, body.requestId(), ErrorCode.PORT_CHANGE_FAILED);
    }

    @Override
    public Long registerAccessChange(String clusterId, AccessChangeRegisterRequestDTO body) {
        return register(clusterId, "/operations/access", body, body.requestId(), ErrorCode.ACCESS_CHANGE_FAILED);
    }

    /**
     * 그룹 작업 등록이 거절된 이유를 본문의 error 필드로 가른다. 사람이 읽는 문장이 아니라 기계 코드라 문구가
     * 바뀌어도 깨지지 않는다. 하나로 묶으면 "이름을 바꿔야 하는 것"과 "다시 누르면 되는 것"을 구분할 수 없다.
     * 시험이 닿도록 패키지 범위로 둔다.
     */
    static BusinessException groupRegistrationError(HttpStatusCode status, String responseBody) {
        String body = responseBody == null ? "" : responseBody;
        BusinessException clusterMismatch = clusterMismatch(body);
        if (clusterMismatch != null) {
            return clusterMismatch;
        }
        for (Map.Entry<String, ErrorCode> rejection : GROUP_REJECTIONS.entrySet()) {
            if (body.contains("\"" + rejection.getKey() + "\"")) {
                return new BusinessException("그룹 작업 등록 거절: " + body, rejection.getValue());
            }
        }
        return WebClientErrorHandler.rejectedOr(status, body, "그룹 작업 등록 실패", ErrorCode.GROUP_CHANGE_FAILED);
    }

    private Long register(String clusterId, String uri, Object body, Long requestId, ErrorCode failureCode) {
        return register(clusterId, uri, body, requestId,
                (status, responseBody) -> registrationError(status, responseBody, failureCode), failureCode);
    }

    /**
     * 409는 보통 같은 번호의 작업이 이미 실행 중이라는 뜻이지만, config-server가 자신의 클러스터 ID와
     * Admin BE가 보낸 값을 비교해 거절할 때도 같은 상태 코드를 쓴다. 본문의 기계 코드를 먼저 확인하지 않으면
     * 잘못 연결된 FARM/LAB URL·토큰을 단순 중복 작업으로 숨겨 운영자가 구성 오류를 발견할 수 없다.
     */
    static BusinessException registrationError(HttpStatusCode status, String responseBody, ErrorCode failureCode) {
        String body = responseBody == null ? "" : responseBody;
        BusinessException clusterMismatch = clusterMismatch(body);
        if (clusterMismatch != null) {
            return clusterMismatch;
        }
        if (status == HttpStatus.CONFLICT) {
            return new BusinessException("이미 처리 중인 작업이 있습니다: " + body,
                    ErrorCode.INVALID_REQUEST_STATUS);
        }
        return WebClientErrorHandler.rejectedOr(status, body, "작업 등록 실패", failureCode);
    }

    private static BusinessException clusterMismatch(String responseBody) {
        return responseBody.contains("\"CLUSTER_ID_MISMATCH\"")
                ? new BusinessException("Config Server 클러스터 식별자가 일치하지 않습니다: " + responseBody,
                        ErrorCode.INTERNAL_CLUSTER_AUTHENTICATION_FAILED)
                : null;
    }

    private Long register(String clusterId, String uri, Object body, Long requestId,
                          BiFunction<HttpStatusCode, String, RuntimeException> rejection, ErrorCode failureCode) {
        // 잘못된 clusterId를 전송 결과 불명으로 감싸지 않는다. registry 선택 자체가 실패하면
        // 요청은 어떤 config-server에도 전송되지 않았으므로 호출자가 구성 오류를 바로 볼 수 있어야 한다.
        WebClient webClient = clients.configWebClient(clusterId);
        Map<?, ?> response;
        try {
            log.info("작업 등록 요청: clusterId={}, uri={}, requestId={}", clusterId, uri, requestId);
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
            log.error("작업 등록 중 예기치 않은 오류: clusterId={}, uri={}, requestId={}",
                    clusterId, uri, requestId, e);
            if (JobResults.neverReachedServer(e)) {
                throw new BusinessException("작업 등록 중 오류: " + e.getMessage(), failureCode, e);
            }
            throw new JobRegistrationUnconfirmedException("작업 등록 결과를 확인하지 못함: " + e.getMessage(), failureCode, e);
        }
        Long jobId = response != null && response.get("job_id") instanceof Number n ? n.longValue() : null;
        log.info("작업 등록 완료: clusterId={}, uri={}, requestId={}, jobId={}",
                clusterId, uri, requestId, jobId);
        return jobId;
    }

    @Override
    public ProvisioningStatusResponseDTO getProvisioningStatus(String clusterId, Long requestId) {
        WebClient webClient = clients.configWebClient(clusterId);
        try {
            ProvisioningStatusResponseDTO response = WebClientErrorHandler.onError(
                            webClient.get()
                                    .uri("/requests/" + requestId + "/status")
                                    .retrieve(),
                            (status, body) -> new BusinessException("생성 진행 상황 조회 실패: " + body,
                                    ErrorCode.EXTERNAL_API_ERROR))
                    .bodyToMono(ProvisioningStatusResponseDTO.class)
                    .timeout(READ_TIMEOUT)
                    .block();

            if (response == null || response.stage() == null) {
                log.error("생성 진행 상황 조회 API가 빈 응답을 반환했습니다. clusterId: {}, requestId: {}",
                        clusterId, requestId);
                throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR);
            }
            return response;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("생성 진행 상황 조회 중 예기치 않은 오류. clusterId: {}, requestId: {}",
                    clusterId, requestId, e);
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR);
        }
    }

    @Override
    public JobStepsResponseDTO getSteps(String clusterId, String kind, Long requestId) {
        WebClient webClient = clients.configWebClient(clusterId);
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
                log.error("작업 단계 기록 조회 API가 빈 응답을 반환했습니다. clusterId: {}, kind: {}, requestId: {}",
                        clusterId, kind, requestId);
                throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR);
            }
            return response;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("작업 단계 기록 조회 중 예기치 않은 오류. clusterId: {}, kind: {}, requestId: {}",
                    clusterId, kind, requestId, e);
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR);
        }
    }

    @Override
    public JobResultResponseDTO getResult(String clusterId, String kind, Long requestId) {
        WebClient webClient = clients.configWebClient(clusterId);
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
                log.error("작업 결과 조회 API가 빈 응답을 반환했습니다. clusterId: {}, kind: {}, requestId: {}",
                        clusterId, kind, requestId);
                throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR);
            }
            return response;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("작업 결과 조회 중 예기치 않은 오류. clusterId: {}, kind: {}, requestId: {}",
                    clusterId, kind, requestId, e);
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR);
        }
    }
}
