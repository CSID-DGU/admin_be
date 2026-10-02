package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.global.alert.AlertDeduplicator;
import DGU_AI_LAB.admin_be.domain.requests.job.JobClient;
import DGU_AI_LAB.admin_be.domain.requests.job.JobResults;
import DGU_AI_LAB.admin_be.domain.alarm.service.AlarmService;
import DGU_AI_LAB.admin_be.domain.nodes.entity.Node;
import DGU_AI_LAB.admin_be.domain.nodes.repository.NodeRepository;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.MigratePodRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.MigrateRegisterRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.RestartPodRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.CreatePodResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.JobResultResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.JobStepsResponseDTO;
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

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;


/**
 * Pod 노드 마이그레이션과 재시작. 재시작은 현재 노드에서 Pod를 다시 만드는 마이그레이션이라 같은 작업·상태(MIGRATING)·
 * 결과 반영을 그대로 쓰고, 시작할 때 무엇을 확인하고 어떤 작업을 등록하는지만 다르다.
 * config-server에 마이그레이션 작업을 등록하고 바로 돌아오며, 결과는
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
    private final NodeRepository nodeRepository;
    private final JobClient jobClient;
    private final PlatformTransactionManager transactionManager;
    private final AlarmService alarmService;
    private final ContainerRestartThrottle restartThrottle;

    // 성공 결과를 받지 못해 반영을 멈춘 신청은 폴러가 매 바퀴 다시 부른다. 같은 알림이 반복되지 않게 한다.
    private final AlertDeduplicator alertDeduplicator;

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

    /** 관리자가 고른 후보 노드 중 하나로 옮긴다. */
    public void startMigration(Long requestId, MigratePodRequestDTO dto) {
        start(requestId, req -> rejectNodesOutsideResourceGroup(req, dto.nodes()), req -> { },
                req -> MigrateRegisterRequestDTO.move(requestId, req.getPodName(), req.getUbuntuUsername(),
                        dto.nodes(), dto.minImprovementRatio(), dto.force()));
    }

    /** 관리자가 현재 노드에서 다시 만든다(GPU 목록 갱신 등). */
    public void startRestart(Long requestId, RestartPodRequestDTO dto) {
        start(requestId, req -> { }, req -> { }, req -> restartJob(req, dto));
    }

    /**
     * 사용자가 본인 컨테이너를 다시 만든다. 남의 신청이면 상태를 드러내지 않도록 상태 전환보다 먼저 거절하고,
     * 횟수는 실제로 시작할 수 있는 요청만 센다(상태 검증에 막힌 요청이 횟수를 쓰지 않게 전환 뒤에 센다).
     */
    public void startOwnRestart(Long userId, Long requestId, RestartPodRequestDTO dto) {
        // start()는 먼저 끝난 작업 결과를 당겨 반영한다. 남의 신청에는 그것도 일어나지 않게 여기서 한 번 거른다
        // (잠금 안에서 다시 확인한다).
        requireOwner(requestRepository.findById(requestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND)), userId);
        start(requestId, req -> requireOwner(req, userId), req -> restartThrottle.acquire(userId),
                req -> restartJob(req, dto));
    }

    private static MigrateRegisterRequestDTO restartJob(Request req, RestartPodRequestDTO dto) {
        return MigrateRegisterRequestDTO.restart(req.getRequestId(), req.getPodName(), req.getUbuntuUsername(),
                dto == null || dto.keepsChanges());
    }

    private static void requireOwner(Request req, Long userId) {
        if (!req.getUser().getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN_REQUEST);
        }
    }

    /**
     * 신청을 MIGRATING으로 바꾸고 작업을 등록한다. 행 잠금과 상태 전환을 같은 트랜잭션에서 커밋해야
     * 동시에 들어온 두 번째 요청이 상태 검증에서 막힌다. 등록이 실패하면 FULFILLED로 되돌린다.
     *
     * @param authorize 상태 전환 전에 거절할 조건
     * @param admit     상태 전환 뒤에 거절할 조건(던지면 전환도 되돌아간다)
     * @param job       등록할 작업 본문
     */
    private void start(Long requestId, Consumer<Request> authorize, Consumer<Request> admit,
                       Function<Request, MigrateRegisterRequestDTO> job) {
        settleFinishedMigration(requestId);
        MigrateRegisterRequestDTO body = new TransactionTemplate(transactionManager).execute(status -> {
            Request req = requestRepository.findByIdForUpdate(requestId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
            authorize.accept(req);
            req.beginMigration();
            admit.accept(req);
            return job.apply(req);
        });
        Long jobId = registerMigration(body);
        // 결과 폴러가 이 번호의 결과만 반영하게 남긴다. 그 사이 끝났거나 되돌려졌으면 건드리지 않는다.
        new TransactionTemplate(transactionManager).execute(status -> {
            requestRepository.findByIdForUpdate(requestId)
                    .filter(r -> r.getStatus() == Status.MIGRATING)
                    .ifPresent(r -> r.recordJob(jobId));
            return null;
        });
        log.info("마이그레이션 작업 등록: requestId={}, username={}, pod={}, recreate={}",
                requestId, body.username(), body.podName(), body.recreate());
    }

    private MigrationResultResponseDTO latestMigrationOf(Long requestId) {
        JobResultResponseDTO job = jobClient.getResult(JobResults.KIND_MIGRATE, requestId);
        return MigrationResultResponseDTO.from(job, completedStepsOf(requestId, job));
    }

    /**
     * 진행 중인 작업에서 이미 끝난 단계 이름. 화면이 지금 어느 단계인지 보여 주는 데만 쓰므로, 단계 기록을
     * 읽지 못해도 결과 조회는 실패시키지 않는다.
     */
    private List<String> completedStepsOf(Long requestId, JobResultResponseDTO job) {
        if (!JobResults.isRunning(job.phase()) || job.jobId() == null) {
            return List.of();
        }
        try {
            return jobClient.getSteps(JobResults.KIND_MIGRATE, requestId).jobs().stream()
                    .filter(j -> job.jobId().equals(j.jobId()) && j.steps() != null)
                    .flatMap(j -> j.steps().stream())
                    .filter(s -> JobResults.PHASE_SUCCESS.equals(s.phase()) && s.action() != null)
                    .map(JobStepsResponseDTO.Step::action)
                    .distinct()
                    .toList();
        } catch (BusinessException e) {
            return List.of();
        }
    }

    /** 본인 신청의 마지막 마이그레이션(재시작) 결과. */
    public MigrationResultResponseDTO getOwnLatestMigration(Long userId, Long requestId) {
        Request req = requestRepository.findById(requestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        requireOwner(req, userId);
        return latestMigrationOf(requestId);
    }

    /**
     * 후보 노드는 신청의 리소스 그룹 안에서만 받는다. 다른 그룹 노드에는 신청한 GPU가 없어서, 그리로 옮기면
     * 새 Pod가 GPU 확인에서 실패하고 신청이 MIGRATING에 남는다. Pod를 만들기 전에 여기서 거절한다.
     */
    private void rejectNodesOutsideResourceGroup(Request req, List<String> nodes) {
        Set<String> allowed = nodeRepository.findAllByResourceGroup(req.getResourceGroup()).stream()
                .map(Node::getNodeId)
                .map(PodMigrationService::normalizeNodeName)
                .collect(Collectors.toSet());
        List<String> outside = nodes.stream()
                .filter(node -> !allowed.contains(normalizeNodeName(node)))
                .toList();
        if (!outside.isEmpty()) {
            throw new BusinessException(
                    "신청한 리소스 그룹에 속하지 않은 노드로는 옮길 수 없습니다: " + String.join(", ", outside),
                    ErrorCode.MIGRATION_NODE_OUTSIDE_RESOURCE_GROUP);
        }
    }

    private static String normalizeNodeName(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 등록이 실패로 보이면 실제로 등록된 작업이 도는지 확인한다. 응답만 늦었으면(타임아웃) 작업은 config-server에서
     * Pod를 옮기는 중이다 — 그때 FULFILLED로 되돌리면 결과 폴러(MIGRATING만 본다)가 결과를 반영하지 못해 신청이
     * 지워진 옛 Pod를 가리키게 된다. 그래서 MIGRATING으로 두고 이어받는다. 요청이 닿지도 않았거나 작업이 없으면
     * FULFILLED로 되돌리고, 작업 상태를 모르면 MIGRATING으로 두어 결과 폴러와 재조정 알림에 맡긴다.
     */
    private Long registerMigration(MigrateRegisterRequestDTO body) {
        Long requestId = body.requestId();
        try {
            return jobClient.registerMigrate(body);
        } catch (RuntimeException e) {
            if (JobResults.neverReachedServer(e)) {
                log.error("config-server에 닿지 못해 마이그레이션 작업이 등록되지 않음 — FULFILLED로 되돌림: requestId={}", requestId, e);
                revertToFulfilled(requestId);
                throw e;
            }
            JobResultResponseDTO job;
            try {
                job = jobClient.getResult(JobResults.KIND_MIGRATE, requestId);
            } catch (Exception lookupFailure) {
                alert(String.format("[마이그레이션 확인 필요] 작업 등록 결과를 확인하지 못해 MIGRATING으로 두었습니다: requestId=%d, error=%s",
                        requestId, e.getMessage()), lookupFailure);
                throw e;
            }
            if (job != null && JobResults.isRunning(job.phase())) {
                log.warn("마이그레이션 작업 등록 응답은 실패했지만 작업이 도는 중 — 이어받음: requestId={}, jobId={}", requestId, job.jobId(), e);
                return job.jobId();
            }
            revertToFulfilled(requestId);
            throw e;
        }
    }

    /**
     * 작업이 성공으로 끝났을 때 반영한다. 옮겼으면 새 Pod·노드·포트로 바꾸고, 건너뛰었으면 그대로 둔 채 FULFILLED로 돌린다.
     * DB 반영이 실패하면 MIGRATING으로 남겨 재마이그레이션을 막고 관리자에게 알린다(실제 Pod는 이미 옮겨졌을 수 있다).
     */
    public void completeMigrationJob(Long requestId, JobResultResponseDTO.Result made) {
        if (made == null) {
            // 성공했는데 결과가 없다(결과 보관 기간이 지남). 옮겼는지 건너뛰었는지 알 수 없으므로 "건너뜀"으로
            // 확정하면 안 된다 — 옮겼다면 신청은 지워진 옛 Pod를 가리키고 새 Pod는 추적되지 않는다.
            // MIGRATING에 둔 채 작업마다 한 번만 알린다. 새 Pod 이름은 작업 단계 기록에서 확인할 수 있다.
            Long jobId = requestRepository.findById(requestId).map(Request::getJobId).orElse(null);
            if (alertDeduplicator.firstOccurrence("migration-missing-result:" + requestId + ":" + jobId)) {
                log.error("마이그레이션 성공 결과에 자원 정보가 없어 신청에 반영하지 못함: requestId={}", requestId);
                alert(String.format("[마이그레이션 확인 필요] 작업은 성공했으나 결과 정보를 받지 못해 신청에 반영하지 못했습니다: requestId=%d", requestId), null);
            }
            return;
        }
        final boolean[] applied = {false};
        final Request[] portsChangedRequest = {null};
        try {
            new TransactionTemplate(transactionManager).execute(status -> {
                Request req = requestRepository.findByIdForUpdate(requestId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
                if (req.getStatus() != Status.MIGRATING) {
                    log.warn("마이그레이션 결과를 반영하려 했으나 상태가 변경됨 - requestId={}, 현재 상태={}", requestId, req.getStatus());
                    return null;
                }
                if (made != null && made.isMigrated()) {
                    Set<String> oldPorts = portKeys(podExternalPortRepository.findByRequestRequestId(requestId));
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
                    if (made.ports() != null && !made.ports().isEmpty() && !oldPorts.equals(newPortKeys(made.ports()))) {
                        // 커밋 뒤 안내 메일에서 쓰는 지연 연관을 트랜잭션 안에서 채워 둔다.
                        req.getUser().getEmail();
                        req.getResourceGroup().getServerName();
                        portsChangedRequest[0] = req;
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
            if (portsChangedRequest[0] != null) {
                notifyPortsChanged(portsChangedRequest[0]);
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
        return latestMigrationOf(requestId);
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

    /** 새 Pod에 다른 포트가 배정되면 사용자에게 새 접속 정보를 알린다. 메일 실패가 반영된 결과를 되돌리지 않는다. */
    private void notifyPortsChanged(Request request) {
        try {
            alarmService.sendContainerPortsChangedEmail(request);
            log.info("마이그레이션 포트 변경 안내 메일 발송: requestId={}", request.getRequestId());
        } catch (Exception e) {
            log.warn("마이그레이션 포트 변경 안내 메일 발송 실패: requestId={}", request.getRequestId(), e);
        }
    }

    private static Set<String> portKeys(List<PodExternalPort> ports) {
        return ports.stream()
                .map(p -> portKey(p.getUsagePurpose(), p.getInternalPort(), p.getExternalPort()))
                .collect(Collectors.toSet());
    }

    private static Set<String> newPortKeys(List<CreatePodResponseDTO.PortInfo> ports) {
        return ports.stream()
                .map(p -> portKey(p.usagePurpose(), p.internalPort(), p.externalPort()))
                .collect(Collectors.toSet());
    }

    private static String portKey(String purpose, Integer internalPort, Integer externalPort) {
        return purpose + ":" + internalPort + ":" + externalPort;
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
