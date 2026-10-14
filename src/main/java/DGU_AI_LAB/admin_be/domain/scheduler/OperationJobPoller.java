package DGU_AI_LAB.admin_be.domain.scheduler;

import DGU_AI_LAB.admin_be.domain.requests.dto.response.JobResultResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.job.JobClient;
import DGU_AI_LAB.admin_be.domain.requests.job.JobResults;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 컨테이너 신청이 아닌 부속 작업(그룹·포트·접속·비밀번호·홈 정리)의 결과를 조회해 반영하는 폴러의 공통 뼈대.
 *
 * <ul>
 *   <li>이번에 등록한 작업이 아닌 결과는 반영하지 않는다</li>
 *   <li>START·RETRY → 다음 바퀴에 다시 본다</li>
 *   <li>SUCCESS → {@link #onSuccess}</li>
 *   <li>그 밖에 끝난 결과(FAIL·UNKNOWN·기록 없음) → {@link #onEndedWithoutSuccess}</li>
 *   <li>한 대상의 조회·반영 실패가 나머지 대상 처리를 막지 않는다. 조회 실패는 대개 일시적이라 다음 바퀴에 다시 본다</li>
 * </ul>
 *
 * <p>컨테이너 작업의 {@link JobResultPoller}와 달리 결과 불명·일부만 반영된 실패도 진행 중 상태에 남겨 두지 않는다.
 * 부속 작업은 이미 맞춰진 조각을 그대로 두고 나머지만 맞추므로, 다시 등록하면 어느 상태에서든 이어서 끝나기 때문이다.
 *
 * @param <T> 진행 중인 작업 한 건을 나타내는 엔티티
 */
@Slf4j
abstract class OperationJobPoller<T> {

    private final JobClient jobClient;
    private final String kind;

    protected OperationJobPoller(JobClient jobClient, String kind) {
        this.jobClient = jobClient;
        this.kind = kind;
    }

    /** 하위 클래스의 {@code @Scheduled} 메서드가 진행 중인 대상을 넘겨 부른다. */
    protected final void pollEach(List<T> inProgress) {
        for (T operation : inProgress) {
            Long recordId = recordId(operation);
            try {
                advance(operation, recordId);
            } catch (Exception e) {
                log.warn("{} 작업 결과 반영 실패 - 다음 바퀴에 다시 본다: id={}", kind, recordId, e);
            }
        }
    }

    private void advance(T operation, Long recordId) {
        Long jobId = jobId(operation);
        if (JobResults.awaitingRegistration(jobId, registrationWaitSince(operation))) {
            return;
        }
        JobResultResponseDTO result = jobClient.getResult(kind, recordId);
        if (JobResults.isFromOtherJob(jobId, result) || JobResults.isRunning(result.phase())) {
            return;
        }
        if (JobResults.PHASE_SUCCESS.equals(result.phase())) {
            onSuccess(operation, result);
            return;
        }
        onEndedWithoutSuccess(operation, result);
    }

    /** 작업 기록의 키. 작업을 등록할 때 이 번호로 등록했다. */
    protected abstract Long recordId(T operation);

    /** 이번에 등록한 작업 번호(등록 결과를 확인하지 못했으면 null). */
    protected abstract Long jobId(T operation);

    /**
     * 작업 번호 없이 진행 중이 된 대상의 등록을 기다리기 시작한 시각. 작업이 아직 기록되지 않았을 수 있어, 이 시각부터
     * {@link JobResults#REGISTRATION_GRACE} 동안은 조회하지 않는다. 기본은 null — 기다리지 않고 바로 조회한다.
     */
    protected LocalDateTime registrationWaitSince(T operation) {
        return null;
    }

    /** 작업이 성공했다. */
    protected abstract void onSuccess(T operation, JobResultResponseDTO result);

    /** 작업이 성공하지 못하고 끝났다(FAIL·UNKNOWN·기록 없음). */
    protected abstract void onEndedWithoutSuccess(T operation, JobResultResponseDTO result);

    /** 실패 사유로 남길 값. 오류 코드가 없으면(결과 불명·기록 없음) 대신 쓸 값을 받는다. */
    protected static String failureCode(JobResultResponseDTO result, String fallback) {
        return result.errorCode() != null ? result.errorCode() : fallback;
    }
}
