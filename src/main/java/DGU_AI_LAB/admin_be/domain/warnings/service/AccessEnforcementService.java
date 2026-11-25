package DGU_AI_LAB.admin_be.domain.warnings.service;

import DGU_AI_LAB.admin_be.domain.alarm.service.AlarmService;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.AccessChangeRegisterRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.job.JobClient;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import DGU_AI_LAB.admin_be.domain.warnings.entity.AccessOperation;
import DGU_AI_LAB.admin_be.domain.warnings.entity.AccessOperationStatus;
import DGU_AI_LAB.admin_be.domain.warnings.repository.AccessOperationRepository;
import DGU_AI_LAB.admin_be.domain.warnings.repository.UserSuspensionRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import DGU_AI_LAB.admin_be.error.exception.EntityNotFoundException;
import DGU_AI_LAB.admin_be.global.util.AfterCommit;
import DGU_AI_LAB.admin_be.global.util.MessageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 계정의 접속을 이용 정지 여부에 맞춘다: 정지 중이면 막혀 있어야 하고, 아니면 열려 있어야 한다.
 *
 * <p>"지금 어때야 하는가"(끝나지 않은 정지가 있는가)와 "지금 어떤가"(마지막으로 성공한 접속 작업)를 비교해 다를 때만
 * config-server 작업을 등록한다. 정지 시작·경고 취소·정지 만료·실패한 작업의 재시도가 모두 이 비교 하나로 처리된다.
 * 예외는 하나다 — 기록은 그 뒤에 새로 만들어진 포트를 모르므로, 정지 중인 계정은 포트가 만들어진 뒤와 주기 점검 때
 * 기록과 상관없이 다시 막는다({@link #reapply}).
 * 결과는 AccessOperationJobPoller 가 {@link #complete}·{@link #fail}로 반영한다.
 *
 * <p>서버별 활성 UbuntuAccount마다 독립된 작업 행과 트랜잭션을 쓴다. PROCESSING 이 보이는 시점에는 그 서버 작업이
 * 이미 등록돼 있어, 폴러가 등록 전의 작업을 "기록 없음"으로 읽어 실패로 닫지 않는다. 뒤 서버 등록이 실패해도 앞서
 * 커밋한 작업은 남아 폴러가 끝까지 추적하며, 실패한 서버만 다음 점검에서 다시 맞춘다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccessEnforcementService {

    private static final int ERROR_CODE_MAX_LENGTH = 64;

    private final AccessOperationRepository operationRepository;
    private final UserSuspensionRepository suspensionRepository;
    private final UserRepository userRepository;
    private final JobClient jobClient;
    private final AlarmService alarmService;
    private final MessageUtils messageUtils;
    private final PlatformTransactionManager transactionManager;

    private record Applied(Long userId, String clusterId, boolean blocked, boolean notifyEnded,
                           String name, String email) {}

    private record Failed(String clusterId, String username, boolean blocked) {}

    /** 이 사용자의 접속을 정지 여부에 맞춘다. 이미 맞거나 작업이 도는 중이면 아무것도 하지 않는다. */
    public void enforce(Long userId) {
        align(userId, false);
    }

    /**
     * 정지 중인 사용자의 차단을 다시 건다. 정지가 시작될 때 돌고 있던 생성·재시작·노드 이동·포트 변경은 차단을
     * 모르고 접속 포트를 새로 만들므로, 그 작업이 끝난 뒤와 주기 점검 때 한 번 더 막는다. 정지 중이 아니면
     * {@link #enforce}와 같다.
     */
    public void reapply(Long userId) {
        align(userId, true);
    }

    /**
     * 정지 중인 사용자와 접속이 막혀 있는 사용자를 모두 다시 맞춘다. 정지 만료와 실패한 작업의 재시도가 여기서 일어난다.
     *
     * <p>정지 중인 사용자는 이미 막혀 있다고 기록돼 있어도 매번 다시 막는다. 기록은 마지막 차단 작업이 성공했다는
     * 것만 말하고, 그 뒤에 열린 채로 만들어진 포트(정지 시작과 겹친 생성·이동·포트 변경)는 알지 못한다. 이미 막힌
     * 포트는 작업이 그대로 두므로 다시 막는 비용은 조회 한 번이다.
     */
    public void enforceAll() {
        Set<Long> suspended = new LinkedHashSet<>(suspensionRepository.findSuspendedUserIds(LocalDateTime.now()));
        Set<Long> userIds = new LinkedHashSet<>(suspended);
        userIds.addAll(operationRepository.findBlockedUserIds(AccessOperationStatus.APPLIED));
        for (Long userId : userIds) {
            try {
                align(userId, suspended.contains(userId));
            } catch (Exception e) {
                // 한 사용자의 실패가 나머지를 막지 않게 한다. 다음 점검 때 다시 맞춘다.
                log.warn("[access] 접속 상태를 맞추지 못함 - 다음 점검 때 다시 본다: userId={}", userId, e);
            }
        }
    }

    private void align(Long userId, boolean reapplyBlock) {
        // 활성 서버 목록을 잠금 안에서 고정한 뒤, 서버마다 별도 트랜잭션으로 등록한다. LAB 등록이 실패해도
        // 이미 커밋된 FARM PROCESSING 행을 잃지 않아 폴러가 실제 작업 결과를 계속 추적할 수 있다.
        List<String> clusters = inTransaction(() -> {
            User user = userRepository.findByIdForUpdate(userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
            return user.getUbuntuAccounts().stream()
                    .filter(account -> account.isActive() && account.getUsername() != null
                            && !account.getUsername().isBlank())
                    .map(account -> account.getServerName())
                    .sorted(Comparator.naturalOrder())
                    .toList();
        });
        for (String clusterId : clusters) {
            alignCluster(userId, clusterId, reapplyBlock);
        }
    }

    /** 한 서버의 외부 작업 등록과 PROCESSING 행 생성을 하나의 독립 트랜잭션으로 묶는다. */
    private void alignCluster(Long userId, String clusterId, boolean reapplyBlock) {
        inTransaction(() -> {
            // 같은 사용자·서버의 등록을 사용자 행 잠금으로 줄 세운다.
            User user = userRepository.findByIdForUpdate(userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
            var account = user.ubuntuAccount(clusterId)
                    .filter(a -> a.isActive() && a.getUsername() != null && !a.getUsername().isBlank())
                    .orElse(null);
            if (account == null) {
                return null;
            }
            boolean wanted = suspensionRepository
                    .findFirstByUser_UserIdAndEndsAtAfterOrderByEndsAtDesc(userId, LocalDateTime.now()).isPresent();
            if (operationRepository.existsByUser_UserIdAndClusterIdAndStatus(
                    userId, clusterId, AccessOperationStatus.PROCESSING)) {
                // 도는 작업이 끝나면 complete 가 다시 맞춘다. 다시 걸기만은 기다리지 않는다 — 도는 차단 작업은 방금
                // 만들어진 포트를 못 봤을 수 있고, 차단끼리는 겹쳐도 결과가 같다. 해제가 도는 중이면 순서가 뒤바뀌어
                // 열린 채로 끝날 수 있으므로 기다린다(해제가 끝난 뒤의 차단이 그 포트까지 막는다).
                boolean overlapSafe = wanted && reapplyBlock && !operationRepository
                        .existsByUser_UserIdAndClusterIdAndStatusAndBlocked(
                                userId, clusterId, AccessOperationStatus.PROCESSING, false);
                if (!overlapSafe) {
                    return null;
                }
            }
            boolean applied = operationRepository
                    .findFirstByUser_UserIdAndClusterIdAndStatusOrderByAccessOperationIdDesc(
                            userId, clusterId, AccessOperationStatus.APPLIED)
                    .map(AccessOperation::isBlocked).orElse(false);
            if (wanted == applied && !(wanted && reapplyBlock)) {
                return null;
            }
            AccessOperation operation = operationRepository.save(
                    new AccessOperation(user, clusterId, account.getUsername(), wanted));
            operation.registered(jobClient.registerAccessChange(clusterId, new AccessChangeRegisterRequestDTO(
                    operation.getAccessOperationId(), account.getUsername(), wanted)));
            log.info("[access] operationId={} cluster={} 접속 {} 등록: userId={}",
                    operation.getAccessOperationId(), clusterId, wanted ? "차단" : "해제", userId);
            return null;
        });
    }

    /** 작업이 성공했다. 이미 끝난 작업이면 아무것도 하지 않는다. */
    public void complete(Long operationId) {
        Applied applied = inTransaction(() -> {
            AccessOperation operation = lock(operationId);
            if (!operation.isProcessing()) {
                return null;
            }
            User user = operation.getUser();
            userRepository.findByIdForUpdate(user.getUserId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
            operation.markApplied();
            boolean suspensionEnded = suspensionRepository
                    .findFirstByUser_UserIdAndEndsAtAfterOrderByEndsAtDesc(
                            user.getUserId(), LocalDateTime.now()).isEmpty();
            boolean notifyEnded = !operation.isBlocked() && suspensionEnded
                    && allActiveAccountsUnblocked(user, operation);
            return new Applied(user.getUserId(), operation.getClusterId(), operation.isBlocked(), notifyEnded,
                    user.getName(), user.getEmail());
        });
        if (applied == null) {
            return;
        }
        log.info("[access] operationId={} cluster={} 접속 {} 반영", operationId, applied.clusterId(),
                applied.blocked() ? "차단" : "해제");
        if (applied.notifyEnded()) {
            AfterCommit.run("이용 정지 종료 안내, userId " + applied.userId(), () -> alarmService.notifyUser(
                    applied.name(), applied.email(),
                    messageUtils.get("notification.user.suspension.ended.subject"),
                    messageUtils.get("notification.user.suspension.ended.body", applied.name())));
        }
        // 작업이 도는 사이 정지가 새로 생기거나 풀렸을 수 있다.
        enforce(applied.userId());
    }

    /** 작업이 성공하지 못했다(실패·결과 불명·기록 없음). 관리자에게 알리고, 다음 점검 때 새 작업으로 다시 맞춘다. */
    public void fail(Long operationId, String errorCode) {
        Failed failed = inTransaction(() -> {
            AccessOperation operation = lock(operationId);
            if (!operation.isProcessing()) {
                return null;
            }
            operation.markFailed(truncate(errorCode));
            return new Failed(operation.getClusterId(), operation.getUsername(), operation.isBlocked());
        });
        log.error("[access] operationId={} cluster={} 작업 실패: error={}", operationId,
                failed == null ? "unknown" : failed.clusterId(), errorCode);
        if (failed != null) {
            alarmService.alertNeedsAction("notification.admin.access.change-failed",
                    failed.blocked() ? "차단" : "해제", failed.username(), operationId, errorCode);
        }
    }

    private AccessOperation lock(Long operationId) {
        return operationRepository.findByIdForUpdate(operationId)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    /** 현재 완료 행을 포함해 모든 활성 서버의 마지막 성공 상태가 해제인지 확인한다. */
    private boolean allActiveAccountsUnblocked(User user, AccessOperation completed) {
        return user.getUbuntuAccounts().stream()
                .filter(account -> account.isActive())
                .allMatch(account -> {
                    if (account.isServer(completed.getClusterId())) {
                        return !completed.isBlocked();
                    }
                    return operationRepository
                            .findFirstByUser_UserIdAndClusterIdAndStatusOrderByAccessOperationIdDesc(
                                    user.getUserId(), account.getServerName(), AccessOperationStatus.APPLIED)
                            .map(operation -> !operation.isBlocked())
                            .orElse(false);
                });
    }

    private static String truncate(String errorCode) {
        return errorCode != null && errorCode.length() > ERROR_CODE_MAX_LENGTH
                ? errorCode.substring(0, ERROR_CODE_MAX_LENGTH) : errorCode;
    }

    private <T> T inTransaction(Supplier<T> work) {
        return new TransactionTemplate(transactionManager).execute(status -> work.get());
    }
}
