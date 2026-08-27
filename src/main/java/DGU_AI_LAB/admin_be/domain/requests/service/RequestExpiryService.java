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
    private final UbuntuAccountService ubuntuAccountService;
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

        // 1. 대상 조회 + lazy 연관 필드 추출 (짧은 트랜잭션, 이후 커넥션 반납)
        final ExpiryContext[] contextRef = {null};
        tx.execute(status -> {
            Request request = requestRepository.findById(requestId)
                    .orElseThrow(() -> new EntityNotFoundException(ErrorCode.ENTITY_NOT_FOUND));

            if (request.getStatus() != Status.FULFILLED) {
                return null;
            }

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

        // 2. 외부 HTTP 호출 (DB 커넥션 미보유).
        //    실패 시 DELETED로 전환하지 않고 예외를 던져 상위 스케줄러(RequestSchedulerService)의
        //    관리자 알림이 실행되도록 한다. 두 API 모두 404를 "이미 삭제됨"으로 처리하므로
        //    다음 스케줄 실행에서 재시도해도 안전하다.
        try {
            podService.deletePod(ctx.podName());
        } catch (Exception e) {
            log.error("[deleteExpiredRequest] Pod 삭제 실패 — DELETED로 전환하지 않음: requestId={}, error={}", requestId, e.getMessage());
            throw new BusinessException("만료 리소스 정리 중 Pod 삭제 실패: " + e.getMessage(), ErrorCode.POD_DELETION_FAILED);
        }

        try {
            ubuntuAccountService.deleteUbuntuAccount(ctx.ubuntuUsername());
        } catch (Exception e) {
            log.error("[deleteExpiredRequest] 우분투 계정 삭제 실패 — DELETED로 전환하지 않음: requestId={}, username={}, error={}", requestId, ctx.ubuntuUsername(), e.getMessage());
            throw new BusinessException("만료 리소스 정리 중 계정 삭제 실패: " + e.getMessage(), ErrorCode.UBUNTU_USER_DELETION_FAILED);
        }

        // 3. 최종 상태 반영 (새 트랜잭션, HTTP 완료 후 짧게만 커넥션 보유)
        //    이벤트는 반드시 이 트랜잭션 안에서 publish해야 한다 — RequestEventListener가
        //    @TransactionalEventListener(AFTER_COMMIT)이라 활성 트랜잭션 없이 publish하면 아예 실행되지 않는다.
        tx.execute(status -> {
            Request request = requestRepository.findById(requestId)
                    .orElseThrow(() -> new EntityNotFoundException(ErrorCode.ENTITY_NOT_FOUND));
            request.deleteAfterCleanup();
            eventPublisher.publishEvent(new RequestExpiredEvent(
                    ctx.userName(), ctx.userEmail(), ctx.ubuntuUsername(), ctx.serverName(), ctx.podName(), ctx.portSummary(), ctx.expiresAt()
            ));
            return null;
        });

        log.info("삭제 트랜잭션 성공: {}", ctx.ubuntuUsername());
    }
}
