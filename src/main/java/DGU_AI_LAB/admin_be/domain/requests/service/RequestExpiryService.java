package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.domain.pod.PodPortUtils;
import DGU_AI_LAB.admin_be.domain.pod.entity.PodExternalPort;
import DGU_AI_LAB.admin_be.domain.pod.repository.PodExternalPortRepository;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import DGU_AI_LAB.admin_be.error.exception.EntityNotFoundException;
import DGU_AI_LAB.admin_be.global.event.RequestExpiredEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RequestExpiryService {

    private final RequestRepository requestRepository;
    private final PodService podService;
    private final PodExternalPortRepository podExternalPortRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final PlatformTransactionManager transactionManager;

    private record ExpiryContext(
            String serverName, String ubuntuUsername, String userName, String userEmail,
            String podName, String expiresAt, String portSummary
    ) {}

    public void deleteExpiredRequest(Long requestId) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        // 1. 행 잠금 조회 + FULFILLED -> EXPIRING 선점 + lazy 연관 필드 추출
        //    (짧은 트랜잭션, 이후 커넥션 반납)
        //    잠금과 상태 전환을 같은 트랜잭션에서 커밋해야, 아래 외부 삭제가 진행되는 동안
        //    DB가 계속 FULFILLED를 가리키는 창이 사라진다. 그 창이 열려 있으면 관리자 삭제·
        //    사용자 취소·변경 요청 승인 같은 다른 경로가 "정상 사용 중인 컨테이너"로 오인해
        //    같은 행을 동시에 건드린다.
        final ExpiryContext[] contextRef = {null};
        tx.execute(status -> {
            Request request = requestRepository.findByIdForUpdate(requestId)
                    .orElseThrow(() -> new EntityNotFoundException(ErrorCode.ENTITY_NOT_FOUND));

            if (request.getStatus() != Status.FULFILLED) {
                return null;
            }
            request.beginExpiry();

            List<PodExternalPort> ports = podExternalPortRepository.findByRequestRequestId(requestId);
            contextRef[0] = new ExpiryContext(
                    request.getResourceGroup().getServerName(),
                    request.getUbuntuUsername(),
                    request.getUser().getName(),
                    request.getUser().getEmail(),
                    request.getPodName(),
                    request.getExpiresAt() != null ? request.getExpiresAt().toLocalDate().toString() : "",
                    PodPortUtils.formatPortSummary(ports)
            );
            return null;
        });

        ExpiryContext ctx = contextRef[0];
        if (ctx == null) {
            return; // FULFILLED 상태가 아니면 정리 대상이 아님
        }

        // 2. 외부 HTTP 호출 (DB 커넥션·행 잠금 미보유). Pod만 지운다.
        //    우분투 계정은 신청이 아니라 웹 계정에 귀속되므로 만료로는 지우지 않는다 —
        //    사용자가 나중에 다시 신청해 승인받으면 같은 유저네임/UID로 같은 홈 디렉터리를
        //    그대로 이어받아야 한다. 계정 회수는 사용자 삭제/비활성화(AdminUserService)에서만 한다.
        //    실패 시 DELETED로 전환하지 않고 EXPIRING -> FULFILLED로 되돌린 뒤 예외를 던져
        //    상위 스케줄러(RequestSchedulerService)의 관리자 알림이 실행되도록 한다.
        //    되돌리지 않으면 다음 스케줄 실행의 FULFILLED 조회에 잡히지 않아 재시도 자체가
        //    사라진다. 삭제 API는 404를 "이미 삭제됨"으로 처리하므로 재시도해도 안전하다.
        try {
            podService.deletePod(ctx.podName(), requestId);
        } catch (Exception e) {
            log.error("[deleteExpiredRequest] Pod 삭제 실패 — DELETED로 전환하지 않음: requestId={}, error={}", requestId, e.getMessage());
            revertToFulfilled(requestId);
            throw new BusinessException("만료 리소스 정리 중 Pod 삭제 실패: " + e.getMessage(), ErrorCode.POD_DELETION_FAILED);
        }

        // 3. 최종 상태 반영 (새 트랜잭션, HTTP 완료 후 짧게만 커넥션 보유).
        //    다시 행을 잠그고 EXPIRING인지 재확인한다 — 외부 삭제가 도는 동안 다른 경로가
        //    상태를 바꿨다면 그 결정을 덮어쓰지 않고 실패시켜야 한다.
        //    이벤트는 반드시 이 트랜잭션 안에서 publish해야 한다 — RequestEventListener가
        //    @TransactionalEventListener(AFTER_COMMIT)이라 활성 트랜잭션 없이 publish하면 아예 실행되지 않는다.
        tx.execute(status -> {
            Request request = requestRepository.findByIdForUpdate(requestId)
                    .orElseThrow(() -> new EntityNotFoundException(ErrorCode.ENTITY_NOT_FOUND));
            if (request.getStatus() != Status.EXPIRING) {
                log.error("[deleteExpiredRequest] 인프라 정리 완료 후 상태가 EXPIRING이 아님 — 수동 확인 필요: requestId={}, 현재 상태={}",
                        requestId, request.getStatus());
                throw new BusinessException(ErrorCode.INVALID_REQUEST_STATUS);
            }
            request.deleteAfterCleanup();
            eventPublisher.publishEvent(new RequestExpiredEvent(
                    ctx.userName(), ctx.userEmail(), ctx.ubuntuUsername(), ctx.serverName(), ctx.podName(), ctx.portSummary(), ctx.expiresAt()
            ));
            return null;
        });

        log.info("삭제 트랜잭션 성공: {}", ctx.ubuntuUsername());
    }

    /**
     * 인프라 정리에 실패했을 때 EXPIRING에 갇힌 요청을 FULFILLED로 되돌린다.
     * 되돌려야 다음 만료 스케줄 실행의 FULFILLED 조회에 다시 잡혀 재시도된다.
     * 이 복구 자체가 실패해도 원래 실패 예외의 전파를 막으면 안 되므로 여기서 삼키고,
     * 정지된 EXPIRING은 재조정 스케줄러가 뒤늦게라도 회수한다.
     */
    private void revertToFulfilled(Long requestId) {
        try {
            new TransactionTemplate(transactionManager).execute(status -> {
                requestRepository.findByIdForUpdate(requestId)
                        .filter(req -> req.getStatus() == Status.EXPIRING)
                        .ifPresent(Request::endExpiry);
                return null;
            });
        } catch (Exception e) {
            log.error("[deleteExpiredRequest] EXPIRING 상태 복구 실패 — 재조정 스케줄러의 회수를 기다린다: requestId={}", requestId, e);
        }
    }

    /**
     * 정리 도중 admin_be 프로세스가 죽으면(강제 재배포, OOM 등) 위의 복구 코드가 실행될
     * 기회조차 없어 요청이 EXPIRING에 영구히 갇힌다. 만료 정리는 두 삭제 API가 모두 404를
     * "이미 삭제됨"으로 처리해 멱등하므로, 방치된 EXPIRING은 FULFILLED로 되돌려 다음 만료
     * 스케줄에서 그대로 재시도시키는 것이 안전하다 (재조정 스케줄러에서 호출).
     */
    public void revertStaleExpiring(Long requestId) {
        revertToFulfilled(requestId);
    }
}
