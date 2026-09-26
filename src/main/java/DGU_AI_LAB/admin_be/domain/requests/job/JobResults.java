package DGU_AI_LAB.admin_be.domain.requests.job;

import DGU_AI_LAB.admin_be.domain.requests.dto.response.JobResultResponseDTO;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 작업 결과를 읽는 규칙. 작업 결과는 신청 번호로만 조회되므로, 같은 신청의 이전 작업 결과와 이번 작업 결과를
 * 가려내는 판정이 모든 결과 반영 경로(폴러·재조정·등록 실패 처리)에 공통으로 필요하다.
 */
public final class JobResults {

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
     * FAIL 중에서, 재시도로 해소되지 않아 실행기가 만든 자원을 되돌리지 않고 관리자에게 넘긴 경우의 error_code.
     * 자원(계정·컨테이너)이 남아 있으므로 일반 실패처럼 신청을 되돌리면 안 된다.
     */
    public static final String ERROR_DEGRADED = "DEGRADED";

    public static final String KIND_PROVISION = "provision";
    public static final String KIND_REVOKE = "revoke";
    public static final String KIND_MIGRATE = "migrate";

    /**
     * 작업 번호가 아직 없는 신청을 기다리는 시간. 등록은 몇 초면 끝나므로, 이보다 오래 번호가 없으면 등록 뒤 기록
     * 전에 admin_be가 멈췄거나 번호 기록 전에 시작된 신청이다 — 그때는 최신 결과를 그대로 쓴다.
     */
    public static final Duration REGISTRATION_GRACE = Duration.ofMinutes(1);

    private JobResults() {
    }

    /**
     * 결과를 아직 보지 말아야 하는가. 상태를 바꾼 뒤 작업을 등록하기 전에 조회하면 같은 신청의 이전 작업 결과가 보인다.
     *
     * @param registeredJobId 이번에 등록한 작업 번호(없으면 null)
     * @param stateChangedAt  작업 대기 상태(PROCESSING·MIGRATING·EXPIRING)로 바꾼 시각(신청의 updatedAt)
     */
    public static boolean awaitingRegistration(Long registeredJobId, LocalDateTime stateChangedAt) {
        return registeredJobId == null && stateChangedAt != null
                && stateChangedAt.isAfter(LocalDateTime.now().minus(REGISTRATION_GRACE));
    }

    /** 이번에 등록한 작업이 아닌 작업(재시도 직후 보이는 이전 작업)의 결과인가. */
    public static boolean isFromOtherJob(Long registeredJobId, JobResultResponseDTO result) {
        return registeredJobId != null && result != null && result.jobId() != null
                && !registeredJobId.equals(result.jobId());
    }

    /** 자원을 남긴 채 관리자에게 넘겨진 실패인가. 되돌리면 남은 자원과 신청 상태가 어긋난다. */
    public static boolean isDegraded(JobResultResponseDTO result) {
        return result != null && ERROR_DEGRADED.equals(result.errorCode());
    }

    /**
     * 등록 요청이 실행기에 닿지도 못했는가(연결 거부·주소 해석 실패). 그렇다면 작업은 등록되지 않은 것이 확실하므로,
     * 등록 실패를 "결과 불명"으로 두지 않고 바로 되돌려도 된다. 연결 뒤의 시간 초과는 요청이 닿았을 수 있어 넣지 않는다.
     */
    public static boolean neverReachedServer(Throwable failure) {
        for (Throwable t = failure; t != null; t = t.getCause()) {
            if (t instanceof ConnectException || t instanceof UnknownHostException) {
                return true;
            }
        }
        return false;
    }

    /** 계정 회수가 이미 없는 계정을 지우려다 실패한 것인가. 목표 상태(계정 없음)에 이미 도달했으므로 성공으로 본다. */
    public static boolean isAccountAlreadyAbsent(JobResultResponseDTO result) {
        String errorCode = result == null ? null : result.errorCode();
        return errorCode != null
                && (errorCode.equalsIgnoreCase("user not found") || errorCode.equalsIgnoreCase("USER_NOT_FOUND"));
    }
}
