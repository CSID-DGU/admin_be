package DGU_AI_LAB.admin_be.domain.scheduler;

import DGU_AI_LAB.admin_be.domain.requests.dto.response.JobResultResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.job.JobClient;
import DGU_AI_LAB.admin_be.domain.requests.job.JobResults;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.global.alert.AlertDeduplicator;
import lombok.extern.slf4j.Slf4j;

/**
 * 작업을 기다리는 상태의 신청마다 작업 결과를 조회해 반영하는 폴러의 공통 뼈대. 생성(PROCESSING)·마이그레이션
 * (MIGRATING)·컨테이너 회수(EXPIRING)가 같은 규칙을 따르고, 결과를 신청에 어떻게 반영할지만 다르다.
 *
 * <ul>
 *   <li>작업 번호가 아직 없는 직후의 신청은 건너뛴다 — 이전 작업의 결과가 보인다</li>
 *   <li>이번에 등록한 작업이 아닌 결과는 반영하지 않는다</li>
 *   <li>SUCCESS → {@link #onSuccess}, 일반 FAIL → {@link #onFailure}</li>
 *   <li>DEGRADED·UNKNOWN → 자원이 남았을 수 있어 신청을 그대로 두고 {@link #onUnresolved}를 작업마다 한 번만 부른다</li>
 *   <li>START·none → 다음 바퀴에 다시 본다(오래 방치되면 RequestSchedulerService 재조정이 판단한다)</li>
 *   <li>한 신청의 조회·반영 실패가 나머지 신청 처리를 막지 않는다</li>
 * </ul>
 */
@Slf4j
public abstract class JobResultPoller {

    private final RequestRepository requestRepository;
    private final JobClient jobClient;
    private final Status watchedStatus;
    private final String kind;
    // 미해결 결과는 신청 상태를 그대로 두므로 다음 바퀴에도 계속 잡힌다. 같은 작업의 알림이 반복되지 않게 한다.
    private final AlertDeduplicator alertDeduplicator;

    protected JobResultPoller(RequestRepository requestRepository, JobClient jobClient, AlertDeduplicator alertDeduplicator,
                              Status watchedStatus, String kind) {
        this.requestRepository = requestRepository;
        this.jobClient = jobClient;
        this.alertDeduplicator = alertDeduplicator;
        this.watchedStatus = watchedStatus;
        this.kind = kind;
    }

    /** 하위 클래스의 {@code @Scheduled} 메서드가 부른다. 주기는 작업 종류마다 설정으로 따로 둔다. */
    protected final void pollOnce() {
        for (Request request : requestRepository.findAllByStatus(watchedStatus)) {
            Long requestId = request.getRequestId();
            if (JobResults.awaitingRegistration(request.getJobId(), request.getUpdatedAt())) {
                continue;
            }
            try {
                JobResultResponseDTO result = jobClient.getResult(kind, requestId);
                if (JobResults.isFromOtherJob(request.getJobId(), result)) {
                    continue;
                }
                dispatch(request, result);
            } catch (Exception e) {
                log.warn("{} 작업 결과 조회·반영 실패 - 다음 바퀴에 다시 본다: requestId={}", kind, requestId, e);
            }
        }
    }

    private void dispatch(Request request, JobResultResponseDTO result) {
        switch (result.phase()) {
            case JobResults.PHASE_SUCCESS -> onSuccess(request, result);
            case JobResults.PHASE_FAIL -> {
                if (JobResults.isDegraded(result)) {
                    reportOnce(request, result);
                    return;
                }
                onFailure(request, result);
            }
            case JobResults.PHASE_UNKNOWN -> reportOnce(request, result);
            default -> {
                // START: 아직 실행 중. none: 등록 이력 없음.
            }
        }
    }

    /** 작업 번호까지 키에 넣는다 — 같은 신청을 다시 처리하다 또 미해결이 되면 새 작업이라 다시 알린다. */
    private void reportOnce(Request request, JobResultResponseDTO result) {
        String eventKey = "job-unresolved:" + kind + ":" + request.getRequestId() + ":" + result.jobId();
        if (alertDeduplicator.firstOccurrence(eventKey)) {
            onUnresolved(request, result);
        }
    }

    /** 작업이 성공했다. */
    protected abstract void onSuccess(Request request, JobResultResponseDTO result);

    /** 작업이 자원을 남기지 않고 실패했다. 신청을 작업 전 상태로 되돌려도 된다. */
    protected abstract void onFailure(Request request, JobResultResponseDTO result);

    /** 자원을 남긴 실패(DEGRADED)이거나 결과 불명이다. 신청을 그대로 두고 사람에게 알린다. 신청마다 한 번만 불린다. */
    protected abstract void onUnresolved(Request request, JobResultResponseDTO result);
}
