package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.global.alert.AlertDeduplicator;
import DGU_AI_LAB.admin_be.domain.requests.job.JobClient;
import DGU_AI_LAB.admin_be.domain.requests.job.JobResults;
import DGU_AI_LAB.admin_be.domain.alarm.service.AlarmService;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.MigratePodRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.MigrateRegisterRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.CreatePodResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.JobResultResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.MigrationResultResponseDTO;
import DGU_AI_LAB.admin_be.domain.pod.entity.PodExternalPort;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.pod.repository.PodExternalPortRepository;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;


/**
 * Pod 노드 마이그레이션. config-server에 마이그레이션 작업을 등록하고 바로 돌아오며, 결과는
 * {@code MigrationJobPoller}가 조회해 {@link #completeMigrationJob}·{@link #failMigrationJob}으로 반영한다.
 * 생성·회수와 같은 작업 방식이라 화면 요청이 새 Pod 준비(수 분)를 기다리지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class PodMigrationService {

    private final RequestRepository requestRepository;
    private final PodExternalPortRepository podExternalPortRepository;
    private final JobClient jobClient;
    private final PlatformTransactionManager transactionManager;
    private final AlarmService alarmService;

    // 성공 결과를 받지 못해 반영을 멈춘 신청은 폴러가 매 바퀴 다시 부른다. 같은 알림이 반복되지 않게 한다.
    private final AlertDeduplicator alertDeduplicator;

    /**
     * 신청을 MIGRATING으로 바꾸고 마이그레이션 작업을 등록한다. 행 잠금과 상태 전환을 같은 트랜잭션에서 커밋해야
     * 동시에 들어온 두 번째 요청이 상태 검증에서 막힌다. 등록이 실패하면 FULFILLED로 되돌린다.
     */
    /**
     * 이미 끝난 마이그레이션 결과를 앞당겨 반영한다.
     *
     * <p>완료 여부는 작업 결과를 직접 조회해 판단하는데, 신청을 MIGRATING에서 되돌리는 것은 3초 주기
     * 폴러다. 그래서 결과가 성공으로 보이는 시점과 신청이 되돌아오는 시점 사이에 틈이 생기고(실측 2초),
     * 그 틈에 들어온 재마이그레이션·회수가 상태 검증에 막힌다. 검증 직전에 한 번 당겨 반영해 없앤다.
     *
     * <p>반영은 폴러가 쓰는 메서드를 그대로 재사용해 두 경로의 동작이 갈리지 않게 한다. 자원을 남긴
     * 실패(DEGRADED)와 결과 불명은 폴러와 같이 신청을 건드리지 않는다. 조회가 실패하면 삼킨다 —
     * 원래 검증이 그대로 판단하면 된다.
     */
    public void settleFinishedMigration(Long requestId) {
        try {
            Request req = requestRepository.findById(requestId).orElse(null);
            if (req == null || req.getStatus() != Status.MIGRATING
                    || JobResults.awaitingRegistration(req.getJobId(), req.getUpdatedAt())) {
                return;
            }
            JobResultResponseDTO result = jobClient.getResult(JobResults.KIND_MIGRATE, requestId);
            if (JobResults.isFromOtherJob(req.getJobId(), result)) {
                return;
            }
            switch (result.phase()) {
                case JobResults.PHASE_SUCCESS -> completeMigrationJob(requestId, result.result());
                case JobResults.PHASE_FAIL -> {
                    if (!JobResults.isDegraded(result)) {
                        failMigrationJob(requestId, result);
                    }
                }
                default -> { }
            }
        } catch (Exception e) {
            log.debug("마이그레이션 결과를 앞당겨 반영하지 못함 - requestId={}", requestId, e);
        }
    }

    public void startMigration(Long requestId, MigratePodRequestDTO dto) {
        settleFinishedMigration(requestId);
        final String[] usernameRef = {null};
        final String[] podNameRef = {null};
        new TransactionTemplate(transactionManager).execute(status -> {
            Request req = requestRepository.findByIdForUpdate(requestId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
            req.beginMigration();
            usernameRef[0] = req.getUbuntuUsername();
            podNameRef[0] = req.getPodName();
            return null;
        });
        Long jobId;
        try {
            jobId = jobClient.registerMigrate(new MigrateRegisterRequestDTO(
                    requestId, podNameRef[0], usernameRef[0], dto.nodes(), dto.minImprovementRatio(), dto.force()));
        } catch (RuntimeException e) {
            revertToFulfilled(requestId);
            throw e;
        }
        // 결과 폴러가 이 번호의 결과만 반영하게 남긴다. 그 사이 끝났거나 되돌려졌으면 건드리지 않는다.
        new TransactionTemplate(transactionManager).execute(status -> {
            requestRepository.findByIdForUpdate(requestId)
                    .filter(r -> r.getStatus() == Status.MIGRATING)
                    .ifPresent(r -> r.recordJob(jobId));
            return null;
        });
        log.info("마이그레이션 작업 등록: requestId={}, username={}, pod={}", requestId, usernameRef[0], podNameRef[0]);
    }

    /**
     * 작업이 성공으로 끝났을 때 반영한다. 옮겼으면 새 Pod·노드·포트로 바꾸고, 건너뛰었으면 그대로 둔 채 FULFILLED로 돌린다.
     * DB 반영이 실패하면 MIGRATING으로 남겨 재마이그레이션을 막고 관리자에게 알린다(실제 Pod는 이미 옮겨졌을 수 있다).
     */
    public void completeMigrationJob(Long requestId, JobResultResponseDTO.Result made) {
        if (made == null) {
            // 성공했는데 결과가 없다(결과 보관 기간이 지남). 옮겼는지 건너뛰었는지 알 수 없으므로 "건너뜀"으로
            // 확정하면 안 된다 — 옮겼다면 신청은 지워진 옛 Pod를 가리키고 새 Pod는 추적되지 않는다.
            // MIGRATING에 둔 채 한 번만 알린다. 새 Pod 이름은 작업 단계 기록에서 확인할 수 있다.
            if (alertDeduplicator.firstOccurrence("migration-missing-result:" + requestId)) {
                log.error("마이그레이션 성공 결과에 자원 정보가 없어 신청에 반영하지 못함: requestId={}", requestId);
                alert(String.format("[마이그레이션 확인 필요] 작업은 성공했으나 결과 정보를 받지 못해 신청에 반영하지 못했습니다: requestId=%d", requestId), null);
            }
            return;
        }
        final boolean[] applied = {false};
        try {
            new TransactionTemplate(transactionManager).execute(status -> {
                Request req = requestRepository.findByIdForUpdate(requestId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
                if (req.getStatus() != Status.MIGRATING) {
                    log.warn("마이그레이션 결과를 반영하려 했으나 상태가 변경됨 - requestId={}, 현재 상태={}", requestId, req.getStatus());
                    return null;
                }
                if (made != null && made.isMigrated()) {
                    req.assignPodInfo(made.podName(), made.node());
                    podExternalPortRepository.deleteByRequestRequestId(requestId);
                    if (made.ports() != null) {
                        for (CreatePodResponseDTO.PortInfo port : made.ports()) {
                            podExternalPortRepository.save(PodExternalPort.builder()
                                    .request(req)
                                    .internalPort(port.internalPort())
                                    .externalPort(port.externalPort())
                                    .usagePurpose(port.usagePurpose())
                                    .build());
                        }
                    }
                }
                req.endMigration();
                applied[0] = true;
                return null;
            });
        } catch (RuntimeException e) {
            alert(String.format("[마이그레이션] 결과 DB 반영 실패 - Pod/포트 상태 수동 확인 필요: requestId=%d", requestId), e);
            throw e;
        }
        if (!applied[0]) {
            return;
        }
        if (made != null && made.isMigrated()) {
            log.info("Pod 마이그레이션 완료: requestId={}, from={}, to={}, newPod={}",
                    requestId, made.fromNode(), made.toNode(), made.podName());
            if ("failed".equals(made.oldPodCleanup())) {
                alert(String.format("[마이그레이션] 새 Pod는 정상 반영됐지만 기존 Pod 정리 실패 - 수동 확인 필요: requestId=%d, oldPod=%s, oldNode=%s",
                        requestId, made.oldPodName(), made.fromNode()), null);
            }
        } else {
            log.info("Pod 마이그레이션 건너뜀: requestId={}, reason={}", requestId, made == null ? null : made.reason());
        }
    }

    /** 작업이 실패로 끝났다. 제어기가 새 Pod를 정리했고 기존 Pod는 그대로이므로 FULFILLED로 되돌리고 알린다. */
    public void failMigrationJob(Long requestId, JobResultResponseDTO result) {
        revertToFulfilled(requestId);
        alert(String.format("[마이그레이션 실패] 기존 컨테이너를 유지합니다: requestId=%d, error=%s",
                requestId, result == null ? null : result.errorCode()), null);
    }

    /** 결과 불명이거나 자원을 남긴 실패. 실제 Pod 상태를 확인하기 전에는 되돌리지 않고 MIGRATING으로 둔 채 알린다. */
    public void reportUnresolvedMigrationJob(Long requestId, JobResultResponseDTO result) {
        alert(String.format("[마이그레이션 확인 필요] 결과를 확정할 수 없어 MIGRATING으로 둡니다. Pod 상태를 확인하세요: requestId=%d, phase=%s, error=%s",
                requestId, result.phase(), result.errorCode()), null);
    }

    /** 신청의 마지막 마이그레이션 작업 결과. */
    public MigrationResultResponseDTO getLatestMigration(Long requestId) {
        if (!requestRepository.existsById(requestId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return MigrationResultResponseDTO.from(jobClient.getResult(JobResults.KIND_MIGRATE, requestId));
    }

    private void revertToFulfilled(Long requestId) {
        try {
            new TransactionTemplate(transactionManager).execute(status -> {
                requestRepository.findByIdForUpdate(requestId)
                        .filter(req -> req.getStatus() == Status.MIGRATING)
                        .ifPresent(Request::endMigration);
                return null;
            });
        } catch (Exception e) {
            alert(String.format("[마이그레이션] MIGRATING 상태 복구 실패 - 수동 확인 필요: requestId=%d", requestId), e);
        }
    }

    private void alert(String message, Exception cause) {
        if (cause != null) {
            log.error(message, cause);
        } else {
            log.warn(message);
        }
        try {
            alarmService.sendSlackAlert(message, null);
        } catch (Exception ignored) {
            // 알림 발송 실패가 원래 흐름을 막으면 안 된다.
        }
    }
}
