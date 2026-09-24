package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.domain.requests.dto.request.MigrateRegisterRequestDTO;

import DGU_AI_LAB.admin_be.domain.requests.dto.request.ProvisionRegisterRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.RevokeRegisterRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.JobHistoryResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.JobResultResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.JobStepsResponseDTO;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import DGU_AI_LAB.admin_be.global.webclient.WebClientErrorHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

/**
 * config-server 작업 인터페이스. 승인·회수를 작업으로 등록하고(POST /operations/provision·revoke)
 * 결과를 조회한다(GET /operations/{kind}/{신청번호}). 실제 실행은 config-server의 제어기가 한다.
 *
 * <p>baseline·noprobe·full 세 방식이 모두 이 인터페이스를 쓴다. 방식 차이(재시도, 결과 확인, 접근 시험)는
 * 제어기 쪽에서만 나므로 admin_be는 방식을 모른다.
 */
@Slf4j
@Service
public class OperationJobService {

    /** 등록 이력이 없다. */
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

    /**
     * FAIL 중에서, 재시도로 해소되지 않아 제어기가 만든 자원을 되돌리지 않고 관리자에게 넘긴 경우의 error_code.
     * 자원(계정·컨테이너)이 남아 있으므로 일반 실패처럼 신청을 되돌리면 안 된다.
     */
    public static final String ERROR_DEGRADED = "DEGRADED";

    public static final String KIND_PROVISION = "provision";
    public static final String KIND_REVOKE = "revoke";
    public static final String KIND_MIGRATE = "migrate";

    /** 신청 생성 시각이 저장된 시간대. 운영 이미지가 TZ=Asia/Seoul로 돈다. */
    static final ZoneId REQUEST_ZONE = ZoneId.of("Asia/Seoul");
    /** admin_be와 config-server의 시계 차이 허용치. 옛 신청의 작업은 몇 시간 이상 앞서므로 넉넉히 둔다. */
    static final Duration CLOCK_SKEW = Duration.ofMinutes(1);

    private final WebClient webClient;
    private final long revokePollMillis;
    private final long revokeTimeoutMillis;

    public OperationJobService(@Qualifier("configWebClient") WebClient configWebClient,
                               @Value("${operations.revoke.poll-ms:2000}") long revokePollMillis,
                               @Value("${operations.revoke.timeout-seconds:900}") long revokeTimeoutSeconds) {
        this.webClient = configWebClient;
        this.revokePollMillis = revokePollMillis;
        this.revokeTimeoutMillis = revokeTimeoutSeconds * 1000;
    }

    /**
     * 생성 작업을 등록한다. 등록만 하고 돌아오므로 계정·컨테이너가 아직 만들어지지 않은 상태에서 반환된다.
     *
     * @return 등록된 작업 번호. 응답에 없으면 null
     * @throws BusinessException 같은 신청의 생성 작업이 아직 끝나지 않았거나(409) 등록 자체가 실패한 경우
     */
    public Long registerProvision(ProvisionRegisterRequestDTO body) {
        return register("/operations/provision", body, body.requestId(), ErrorCode.POD_CREATION_FAILED);
    }

    /**
     * 마이그레이션 작업을 등록한다. 결과는 {@code MigrationJobPoller}가 조회해 신청에 반영한다.
     *
     * @throws BusinessException 같은 신청의 마이그레이션 작업이 아직 끝나지 않았거나(409) 등록이 거절·실패한 경우
     */
    public void registerMigrate(MigrateRegisterRequestDTO body) {
        register("/operations/migrate", body, body.requestId(), ErrorCode.POD_MIGRATION_FAILED);
    }

    /** 회수 작업을 등록한다. 결과를 기다리지 않는다. */
    public void registerRevoke(RevokeRegisterRequestDTO body) {
        register("/operations/revoke", body, body.requestId(), ErrorCode.POD_DELETION_FAILED);
    }

    /**
     * 회수 작업을 등록하고 끝날 때까지 기다린다. 만료 정리·사용자 정리처럼 회수 결과를 보고 다음 단계
     * (신청 DELETED 전환, 계정 회수, UID/GID 반환)를 정하는 호출자가 쓴다.
     *
     * <p>같은 신청의 회수 작업이 이미 도는 중이면(409) 새로 등록하지 않고 그 작업의 결과를 기다린다.
     * 이미 없는 계정을 지우려다 실패한 것은 성공으로 본다 — 목표 상태(계정 없음)에 이미 도달했기 때문이다.
     *
     * @param failureCode 작업이 실패·결과 불명으로 끝났거나 제한 시간 안에 끝나지 않았을 때 던질 오류 코드
     */
    public void revokeAndWait(RevokeRegisterRequestDTO body, ErrorCode failureCode) {
        Long requestId = body.requestId();
        try {
            register("/operations/revoke", body, requestId, failureCode);
        } catch (BusinessException e) {
            if (e.getErrorCode() != ErrorCode.INVALID_REQUEST_STATUS) {
                throw e;
            }
            log.info("같은 신청의 회수 작업이 이미 진행 중 — 그 결과를 기다린다: requestId={}", requestId);
        }

        long deadline = System.currentTimeMillis() + revokeTimeoutMillis;
        while (true) {
            JobResultResponseDTO result = null;
            try {
                result = getResult(KIND_REVOKE, requestId);
            } catch (BusinessException e) {
                // 조회 한 번의 실패로 회수 전체를 실패로 만들지 않는다. 제한 시간까지 다시 조회한다.
                log.warn("회수 작업 결과 조회 실패, 다시 조회한다: requestId={}", requestId, e);
            }
            if (result != null) {
                switch (result.phase()) {
                    case PHASE_SUCCESS -> {
                        return;
                    }
                    case PHASE_FAIL -> {
                        if (body.deleteAccount() && isAccountAlreadyAbsent(result.errorCode())) {
                            log.info("회수할 계정이 이미 없어 삭제된 것으로 처리: requestId={}, username={}",
                                    requestId, body.username());
                            return;
                        }
                        throw new BusinessException("회수 작업 실패: " + result.errorCode(), failureCode);
                    }
                    case PHASE_UNKNOWN ->
                            throw new BusinessException("회수 작업 결과 불명: " + result.errorCode(), failureCode);
                    default -> {
                        // START: 아직 실행 중. none: 등록 직후 조회가 먼저 도착한 경우.
                    }
                }
            }
            if (System.currentTimeMillis() >= deadline) {
                throw new BusinessException("회수 작업이 제한 시간 안에 끝나지 않음: requestId=" + requestId, failureCode);
            }
            sleep(revokePollMillis, failureCode);
        }
    }

    /** 자원을 남긴 채 관리자에게 넘겨진 실패인가. 되돌리면 남은 자원과 신청 상태가 어긋난다. */
    public static boolean isDegraded(JobResultResponseDTO result) {
        return result != null && ERROR_DEGRADED.equals(result.errorCode());
    }

    private static boolean isAccountAlreadyAbsent(String errorCode) {
        return errorCode != null
                && (errorCode.equalsIgnoreCase("user not found") || errorCode.equalsIgnoreCase("USER_NOT_FOUND"));
    }

    private static void sleep(long millis, ErrorCode failureCode) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("회수 작업 대기 중 중단됨", failureCode);
        }
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
            throw new BusinessException(failureCode);
        }
        Long jobId = response != null && response.get("job_id") instanceof Number n ? n.longValue() : null;
        log.info("작업 등록 완료: {}, requestId: {}, jobId: {}", uri, requestId, jobId);
        return jobId;
    }

    /**
     * 신청 상세 화면용: 한 신청의 생성·회수 작업 단계 기록을 함께 조회한다.
     *
     * <p>config-server의 작업 기록은 신청 번호로만 묶인다. 신청 DB가 초기화돼 번호가 다시 매겨지면 같은 번호를 쓰던
     * 옛 신청의 작업이 함께 조회되므로, 신청이 만들어지기 전에 시작된 작업은 뺀다.
     *
     * @param requestCreatedAt 신청 생성 시각(서버 시간대 {@link #REQUEST_ZONE} 기준으로 저장된 값)
     */
    public JobHistoryResponseDTO getJobHistory(Long requestId, LocalDateTime requestCreatedAt) {
        Instant notBefore = requestCreatedAt.atZone(REQUEST_ZONE).toInstant().minus(CLOCK_SKEW);
        return new JobHistoryResponseDTO(
                withoutJobsStartedBefore(getSteps(KIND_PROVISION, requestId), notBefore),
                withoutJobsStartedBefore(getSteps(KIND_REVOKE, requestId), notBefore),
                withoutJobsStartedBefore(getSteps(KIND_MIGRATE, requestId), notBefore));
    }

    private static JobStepsResponseDTO withoutJobsStartedBefore(JobStepsResponseDTO steps, Instant notBefore) {
        List<JobStepsResponseDTO.Job> jobs = steps.jobs().stream()
                .filter(job -> !startedBefore(job, notBefore))
                .toList();
        return new JobStepsResponseDTO(steps.requestId(), steps.kind(), jobs);
    }

    /** 시작 시각을 읽을 수 없는 작업은 거르지 않는다. 보이는 편이 조용히 사라지는 것보다 낫다. */
    private static boolean startedBefore(JobStepsResponseDTO.Job job, Instant notBefore) {
        if (job.startedAt() == null) {
            return false;
        }
        try {
            return Instant.parse(job.startedAt()).isBefore(notBefore);
        } catch (DateTimeParseException e) {
            log.warn("작업 시작 시각을 읽지 못함: jobId={}, startedAt={}", job.jobId(), job.startedAt());
            return false;
        }
    }

    /**
     * 작업 단계 기록을 조회한다. 작업이 없으면 jobs가 빈 목록으로 온다.
     *
     * @param kind {@link #KIND_PROVISION} 또는 {@link #KIND_REVOKE}
     */
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
