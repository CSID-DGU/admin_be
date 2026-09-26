package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.domain.pod.PodPortUtils;
import DGU_AI_LAB.admin_be.domain.pod.entity.PodExternalPort;
import DGU_AI_LAB.admin_be.domain.pod.repository.PodExternalPortRepository;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.RevokeRegisterRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.JobResultResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import DGU_AI_LAB.admin_be.error.exception.EntityNotFoundException;
import DGU_AI_LAB.admin_be.global.event.RequestContainerDeletedEvent;
import DGU_AI_LAB.admin_be.global.event.RequestExpiredEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 신청에 딸린 컨테이너의 회수. 회수 작업을 등록하고 바로 돌아오며(FULFILLED → EXPIRING), 결과는
 * {@code RevokeJobPoller}가 조회해 {@link #completeContainerRevoke}(DELETED) 또는
 * {@link #failContainerRevoke}(FULFILLED로 되돌림)로 반영한다.
 *
 * <p>우분투 계정은 신청이 아니라 웹 계정에 귀속되므로 여기서 지우지 않는다 — 사용자가 나중에 다시 신청해
 * 승인받으면 같은 유저네임/UID로 같은 홈 디렉터리를 그대로 이어받아야 한다. 계정 회수는 사용자
 * 삭제/비활성화(AdminUserService)에서만 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RequestExpiryService {

    private static final ZoneId REQUEST_ZONE = ZoneId.of("Asia/Seoul");

    private final RequestRepository requestRepository;
    private final OperationJobService operationJobService;
    private final PodExternalPortRepository podExternalPortRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final PlatformTransactionManager transactionManager;

    /** 만료된 신청의 컨테이너 회수를 시작한다. FULFILLED가 아니면 만료 스케줄러가 다음 회차에 다시 본다. */
    public void deleteExpiredRequest(Long requestId) {
        startContainerRevoke(requestId);
    }

    /**
     * 관리자가 화면에서 컨테이너 하나만 회수한다. 우분투 계정과 홈 디렉터리는 남고, 같은 사용자의
     * 다른 신청은 건드리지 않는다 — 계정까지 회수하려면 사용자 관리의 계정 회수를 써야 한다.
     *
     * <p>대상이 FULFILLED가 아니면 조용히 넘기지 않고 409로 알린다 — 만료 정리는 스케줄러가 훑다가
     * 지나치는 것이 맞지만, 관리자가 누른 버튼은 결과를 돌려줘야 한다.
     */
    public void deleteContainerByAdmin(Long requestId) {
        if (!startContainerRevoke(requestId)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST_STATUS);
        }
    }

    /**
     * 컨테이너 회수 작업을 등록한다. 행을 잠그고 FULFILLED → EXPIRING으로 선점한 뒤 커밋하고 나서 등록한다 —
     * 선점을 먼저 커밋해야 작업이 도는 동안 다른 경로(관리자 삭제·변경 승인·마이그레이션)가 "정상 사용 중인
     * 컨테이너"로 오인해 같은 행을 건드리지 않는다.
     *
     * @return 회수를 시작했으면 true, 신청이 FULFILLED가 아니라 시작하지 않았으면 false
     * @throws BusinessException 등록에 실패해 FULFILLED로 되돌린 경우
     */
    public boolean startContainerRevoke(Long requestId) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        final String[] podNameRef = {null};
        Boolean started = tx.execute(status -> {
            Request request = requestRepository.findByIdForUpdate(requestId)
                    .orElseThrow(() -> new EntityNotFoundException(ErrorCode.ENTITY_NOT_FOUND));
            if (request.getStatus() != Status.FULFILLED) {
                return false;
            }
            request.beginExpiry();
            podNameRef[0] = request.getPodName();
            return true;
        });
        if (!Boolean.TRUE.equals(started)) {
            return false;
        }

        Long jobId = registerContainerRevoke(requestId, podNameRef[0]);
        // 결과 폴러가 이 번호의 결과만 반영하게 남긴다. 그 사이 상태가 바뀌었으면 건드리지 않는다.
        tx.execute(status -> {
            requestRepository.findByIdForUpdate(requestId)
                    .filter(r -> r.getStatus() == Status.EXPIRING)
                    .ifPresent(r -> r.recordJob(jobId));
            return null;
        });
        log.info("컨테이너 회수 작업 등록: requestId={}, pod={}, jobId={}", requestId, podNameRef[0], jobId);
        return true;
    }

    /**
     * 등록이 실패로 보이면 실제로 등록된 작업이 도는지 확인한다. 응답만 늦었거나(타임아웃) 같은 신청의 회수가 이미
     * 도는 경우(409) 작업은 config-server에 있으므로 EXPIRING으로 두고 결과 폴러에 맡긴다. 작업이 없으면
     * FULFILLED로 되돌려 다음 만료 회차나 관리자의 재시도가 다시 잡게 한다.
     */
    private Long registerContainerRevoke(Long requestId, String podName) {
        try {
            return operationJobService.registerRevoke(
                    new RevokeRegisterRequestDTO(requestId, podName, null, null, false), ErrorCode.POD_DELETION_FAILED);
        } catch (Exception e) {
            if (OperationJobService.neverReachedServer(e)) {
                log.error("config-server에 닿지 못해 회수 작업이 등록되지 않음 — FULFILLED로 되돌림: requestId={}", requestId, e);
                revertToFulfilled(requestId);
                throw asBusiness(e);
            }
            JobResultResponseDTO job = null;
            try {
                job = operationJobService.getResult(OperationJobService.KIND_REVOKE, requestId);
            } catch (Exception lookupFailure) {
                log.warn("회수 작업 등록 실패 후 작업 상태 조회도 실패 — EXPIRING 유지, 재조정에 맡김: requestId={}", requestId, lookupFailure);
                throw asBusiness(e);
            }
            if (job != null && OperationJobService.PHASE_START.equals(job.phase())) {
                log.warn("회수 작업 등록 응답은 실패했지만 작업이 도는 중 — 이어받음: requestId={}, jobId={}", requestId, job.jobId(), e);
                return job.jobId();
            }
            log.error("컨테이너 회수 작업 등록 실패 — FULFILLED로 되돌림: requestId={}", requestId, e);
            revertToFulfilled(requestId);
            throw asBusiness(e);
        }
    }

    private static BusinessException asBusiness(Exception e) {
        return e instanceof BusinessException be ? be
                : new BusinessException("컨테이너 회수 작업 등록 실패: " + e.getMessage(), ErrorCode.POD_DELETION_FAILED);
    }

    /**
     * 회수 작업이 성공했을 때 신청을 DELETED로 바꾸고 외부 포트를 회수한 뒤 안내한다. 결과 폴러가 호출한다.
     * 만료일이 지난 신청이면 만료 안내를, 아니면 회수 안내를 보낸다.
     */
    public void completeContainerRevoke(Long requestId) {
        new TransactionTemplate(transactionManager).execute(status -> {
            Request request = requestRepository.findByIdForUpdate(requestId)
                    .orElseThrow(() -> new EntityNotFoundException(ErrorCode.ENTITY_NOT_FOUND));
            if (request.getStatus() != Status.EXPIRING) {
                log.warn("컨테이너 회수 성공을 반영하려 했으나 상태가 변경됨: requestId={}, 현재 상태={}",
                        requestId, request.getStatus());
                return null;
            }
            // 외부 포트는 Pod에 딸린 자원이라 Pod와 함께 회수한다. 남겨 두면 DELETED 신청이
            // 포트 번호를 계속 쥔 것처럼 보인다. 안내 문구용 포트 요약은 지우기 전에 떠 둔다.
            List<PodExternalPort> ports = podExternalPortRepository.findByRequestRequestId(requestId);
            String portSummary = PodPortUtils.formatPortSummary(ports);
            request.deleteAfterCleanup();
            podExternalPortRepository.deleteByRequestRequestId(requestId);
            // 이벤트는 이 트랜잭션 안에서 publish해야 한다 — RequestEventListener가
            // @TransactionalEventListener(AFTER_COMMIT)이라 활성 트랜잭션 없이 publish하면 실행되지 않는다.
            eventPublisher.publishEvent(isExpired(request)
                    ? new RequestExpiredEvent(request.getUser().getName(), request.getUser().getEmail(),
                            request.getUbuntuUsername(), request.getResourceGroup().getServerName(),
                            request.getPodName(), portSummary, request.getExpiresAt().toLocalDate().toString())
                    : new RequestContainerDeletedEvent(request.getUser().getName(), request.getUser().getEmail(),
                            request.getUbuntuUsername(), request.getResourceGroup().getServerName(),
                            request.getPodName(), portSummary));
            log.info("컨테이너 회수 완료: requestId={}, pod={}", requestId, request.getPodName());
            return null;
        });
    }

    private static boolean isExpired(Request request) {
        return request.getExpiresAt() != null
                && !request.getExpiresAt().isAfter(LocalDateTime.now(REQUEST_ZONE));
    }

    /**
     * 회수 작업이 실패했을 때 EXPIRING을 FULFILLED로 되돌린다. 결과 폴러가 호출한다.
     * 되돌려야 다음 만료 스케줄의 FULFILLED 조회에 다시 잡혀 재시도된다. 회수 작업은 없는 Pod를
     * "이미 삭제됨"으로 처리해 멱등하므로 재시도해도 안전하다.
     */
    public void failContainerRevoke(Long requestId) {
        revertToFulfilled(requestId);
    }

    /**
     * 회수 작업이 등록되지 않은 채(등록 전에 admin_be가 멈춤) 방치된 EXPIRING을 FULFILLED로 되돌린다.
     * 재조정 스케줄러가 호출한다.
     */
    public void revertStaleExpiring(Long requestId) {
        revertToFulfilled(requestId);
    }

    /** 이 복구 자체가 실패해도 원래 흐름을 막지 않는다. 정지된 EXPIRING은 재조정 스케줄러가 다시 본다. */
    private void revertToFulfilled(Long requestId) {
        try {
            new TransactionTemplate(transactionManager).execute(status -> {
                requestRepository.findByIdForUpdate(requestId)
                        .filter(req -> req.getStatus() == Status.EXPIRING)
                        .ifPresent(Request::endExpiry);
                return null;
            });
        } catch (Exception e) {
            log.error("EXPIRING 상태 복구 실패 — 재조정 스케줄러의 회수를 기다린다: requestId={}", requestId, e);
        }
    }
}
