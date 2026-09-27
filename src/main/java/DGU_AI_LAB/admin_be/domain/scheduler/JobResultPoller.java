package DGU_AI_LAB.admin_be.domain.scheduler;

import DGU_AI_LAB.admin_be.domain.requests.dto.response.JobResultResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.job.JobClient;
import DGU_AI_LAB.admin_be.domain.requests.job.JobResults;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 작업을 기다리는 상태의 신청마다 작업 결과를 조회해 반영하는 폴러의 공통 뼈대. 생성(PROCESSING)·마이그레이션
 * (MIGRATING)·컨테이너 회수(EXPIRING)가 같은 규칙을 따르고, 결과를 신청에 어떻게 반영할지만 다르다.
 *
 * <ul>
 *   <li>작업 번호가 아직 없는 직후의 신청은 건너뛴다 — 이전 작업의 결과가 보인다</li>
 *   <li>이번에 등록한 작업이 아닌 결과는 반영하지 않는다</li>
 *   <li>SUCCESS → {@link #onSuccess}, 일반 FAIL → {@link #onFailure}</li>
 *   <li>DEGRADED·UNKNOWN → 자원이 남았을 수 있어 신청을 그대로 두고 {@link #onUnresolved}를 신청마다 한 번만 부른다</li>
 *   <li>START·RETRY·none → 다음 바퀴에 다시 본다(오래 방치되면 RequestSchedulerService 재조정이 판단한다)</li>
 *   <li>한 신청의 조회·반영 실패가 나머지 신청 처리를 막지 않는다. 결과 조회 자체의 실패(네트워크 순단 등
 *       흔하고 대개 다음 바퀴에 스스로 회복됨)는 로그만 남기고 조용히 넘어간다 — 조회 실패마다 알리면 소음이 된다.
 *       반면 반영(onSuccess/onFailure/onUnresolved) 중 예외가 나면(예: 계정에 이미 다른 uid가 배정됨) 원인이
 *       해소될 때까지 매 바퀴 반복될 수 있는데도 지금까지 아무 알림이 없었다 — 신청마다 한 번은
 *       {@link #onApplyFailed}로 알린다</li>
 * </ul>
 */
@Slf4j
public abstract class JobResultPoller {

    private final RequestRepository requestRepository;
    private final JobClient jobClient;
    private final Status watchedStatus;
    private final String kind;

    // 미해결 결과는 신청 상태를 그대로 두므로 다음 바퀴에도 계속 잡힌다. 같은 알림이 반복되지 않도록 알린 신청을 기억한다.
    private final Set<Long> reported = ConcurrentHashMap.newKeySet();
    // 결과 반영 자체가 예외로 실패한 신청. dispatch()의 reported와 별개다 — 같은 Set을 쓰면 SUCCESS 분기가
    // 매번 무조건 reported.remove(...)부터 하고 onSuccess를 부르므로, onSuccess가 매번 같은 예외로 실패해도
    // "한 번 지웠다가 바로 다시 추가"를 반복해 매 바퀴 알림이 나간다. 조회·반영 전체가 예외 없이 끝난 바퀴에만 지운다.
    private final Set<Long> applyFailureReported = ConcurrentHashMap.newKeySet();

    protected JobResultPoller(RequestRepository requestRepository, JobClient jobClient, Status watchedStatus, String kind) {
        this.requestRepository = requestRepository;
        this.jobClient = jobClient;
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
            JobResultResponseDTO result;
            try {
                result = jobClient.getResult(kind, requestId);
            } catch (Exception e) {
                // 조회 실패는 대개 일시적(네트워크 순단 등)이라 다음 바퀴에 스스로 회복된다. 매번 알리면 소음이라
                // 로그만 남긴다 — 반영 실패(아래)와 달리 원인을 사람이 봐야 할 가능성이 낮다.
                log.warn("{} 작업 결과 조회 실패 - 다음 바퀴에 다시 본다: requestId={}", kind, requestId, e);
                continue;
            }
            if (JobResults.isFromOtherJob(request.getJobId(), result)) {
                continue;
            }
            try {
                dispatch(request, result);
                applyFailureReported.remove(requestId);
            } catch (Exception e) {
                // 조회는 됐지만 반영(onSuccess/onFailure/onUnresolved) 중 예외가 났다 — 예: 계정에 이미 다른
                // uid가 배정됨. 원인이 해소되지 않으면 다음 바퀴에도 같은 예외가 반복될 수 있어 신청마다 한 번은 알린다.
                log.warn("{} 결과 반영 실패 - 다음 바퀴에 다시 시도: requestId={}", kind, requestId, e);
                if (applyFailureReported.add(requestId)) {
                    onApplyFailed(request, e);
                }
            }
        }
    }

    private void dispatch(Request request, JobResultResponseDTO result) {
        Long requestId = request.getRequestId();
        switch (result.phase()) {
            case JobResults.PHASE_SUCCESS -> {
                reported.remove(requestId);
                onSuccess(request, result);
            }
            case JobResults.PHASE_FAIL -> {
                if (JobResults.isDegraded(result)) {
                    reportOnce(request, result);
                    return;
                }
                reported.remove(requestId);
                onFailure(request, result);
            }
            case JobResults.PHASE_UNKNOWN -> reportOnce(request, result);
            default -> {
                // START: 아직 실행 중. none: 등록 이력 없음.
            }
        }
    }

    private void reportOnce(Request request, JobResultResponseDTO result) {
        if (reported.add(request.getRequestId())) {
            onUnresolved(request, result);
        }
    }

    /** 작업이 성공했다. */
    protected abstract void onSuccess(Request request, JobResultResponseDTO result);

    /** 작업이 자원을 남기지 않고 실패했다. 신청을 작업 전 상태로 되돌려도 된다. */
    protected abstract void onFailure(Request request, JobResultResponseDTO result);

    /** 자원을 남긴 실패(DEGRADED)이거나 결과 불명이다. 신청을 그대로 두고 사람에게 알린다. 신청마다 한 번만 불린다. */
    protected abstract void onUnresolved(Request request, JobResultResponseDTO result);

    /**
     * 결과 조회 또는 반영(onSuccess/onFailure/onUnresolved) 중 예외가 났다. 신청마다 한 번만 불린다(다음 바퀴에
     * 예외 없이 끝나면 다시 알릴 수 있게 초기화된다). 기본은 아무 것도 하지 않는다 — 대부분의 조회 실패(네트워크
     * 순단 등)는 다음 바퀴에 자연히 회복되므로, 재시도해도 반복될 만한 예외만 하위 클래스가 골라 알린다.
     */
    protected void onApplyFailed(Request request, Exception e) {
    }
}
