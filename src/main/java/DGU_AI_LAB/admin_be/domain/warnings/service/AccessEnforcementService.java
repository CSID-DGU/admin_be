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
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 계정의 접속을 이용 정지 여부에 맞춘다: 정지 중이면 막혀 있어야 하고, 아니면 열려 있어야 한다.
 *
 * <p>"지금 어때야 하는가"(끝나지 않은 정지가 있는가)와 "지금 어떤가"(마지막으로 성공한 접속 작업)를 비교해 다를 때만
 * config-server 작업을 등록한다. 정지 시작·경고 취소·정지 만료·실패한 작업의 재시도가 모두 이 비교 하나로 처리된다.
 * 결과는 AccessOperationJobPoller 가 {@link #complete}·{@link #fail}로 반영한다.
 *
 * <p>작업 등록은 트랜잭션 안에서 한다. PROCESSING 이 보이는 시점에는 그 작업이 이미 등록돼 있어, 폴러가 등록 전의
 * 작업을 "기록 없음"으로 읽어 실패로 닫지 않는다. 등록이 실패하면 아무것도 남지 않고, 다음 점검 때 다시 맞춘다.
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

    private record Applied(Long userId, boolean blocked, String name, String email) {}

    private record Failed(String username, boolean blocked) {}

    /** 이 사용자의 접속을 정지 여부에 맞춘다. 이미 맞거나 작업이 도는 중이면 아무것도 하지 않는다. */
    public void enforce(Long userId) {
        align(userId, false);
    }

    /**
     * 정지 중인 사용자의 차단을 다시 건다. 정지가 시작될 때 돌고 있던 재시작·노드 이동은 차단을 모르고 접속 포트를
     * 새로 만들므로, 그 작업이 끝난 뒤 한 번 더 막는다. 정지 중이 아니면 {@link #enforce}와 같다.
     */
    public void reapply(Long userId) {
        align(userId, true);
    }

    /** 정지 중인 사용자와 접속이 막혀 있는 사용자를 모두 다시 맞춘다. 정지 만료와 실패한 작업의 재시도가 여기서 일어난다. */
    public void enforceAll() {
        Set<Long> userIds = new LinkedHashSet<>(suspensionRepository.findSuspendedUserIds(LocalDateTime.now()));
        userIds.addAll(operationRepository.findBlockedUserIds(AccessOperationStatus.APPLIED));
        for (Long userId : userIds) {
            try {
                enforce(userId);
            } catch (Exception e) {
                // 한 사용자의 실패가 나머지를 막지 않게 한다. 다음 점검 때 다시 맞춘다.
                log.warn("[access] 접속 상태를 맞추지 못함 - 다음 점검 때 다시 본다: userId={}", userId, e);
            }
        }
    }

    private void align(Long userId, boolean reapplyBlock) {
        inTransaction(() -> {
            // 같은 사용자의 작업 등록을 사용자 행 잠금으로 줄 세운다.
            User user = userRepository.findByIdForUpdate(userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
            if (operationRepository.existsByUser_UserIdAndStatus(userId, AccessOperationStatus.PROCESSING)) {
                // 도는 작업이 끝나면 complete 가 다시 맞춘다.
                return null;
            }
            boolean wanted = suspensionRepository
                    .findFirstByUser_UserIdAndEndsAtAfterOrderByEndsAtDesc(userId, LocalDateTime.now()).isPresent();
            boolean applied = operationRepository
                    .findFirstByUser_UserIdAndStatusOrderByAccessOperationIdDesc(userId, AccessOperationStatus.APPLIED)
                    .map(AccessOperation::isBlocked).orElse(false);
            if (wanted == applied && !(wanted && reapplyBlock)) {
                return null;
            }
            if (user.getUbuntuUsername() == null || user.getUbuntuUsername().isBlank()) {
                // 리눅스 계정을 정한 적이 없으면 컨테이너도 없다. 막을 포트가 없다.
                return null;
            }
            AccessOperation operation = operationRepository.save(new AccessOperation(user, wanted));
            operation.registered(jobClient.registerAccessChange(new AccessChangeRegisterRequestDTO(
                    operation.getAccessOperationId(), operation.getUsername(), wanted)));
            log.info("[access] operationId={} 접속 {} 등록: userId={}",
                    operation.getAccessOperationId(), wanted ? "차단" : "해제", userId);
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
            operation.markApplied();
            User user = operation.getUser();
            return new Applied(user.getUserId(), operation.isBlocked(), user.getName(), user.getEmail());
        });
        if (applied == null) {
            return;
        }
        log.info("[access] operationId={} 접속 {} 반영", operationId, applied.blocked() ? "차단" : "해제");
        if (!applied.blocked()) {
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
            return new Failed(operation.getUsername(), operation.isBlocked());
        });
        log.error("[access] operationId={} 작업 실패: error={}", operationId, errorCode);
        if (failed != null) {
            alarmService.alertNeedsAction("notification.admin.access.change-failed",
                    failed.blocked() ? "차단" : "해제", failed.username(), operationId, errorCode);
        }
    }

    private AccessOperation lock(Long operationId) {
        return operationRepository.findByIdForUpdate(operationId)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private static String truncate(String errorCode) {
        return errorCode != null && errorCode.length() > ERROR_CODE_MAX_LENGTH
                ? errorCode.substring(0, ERROR_CODE_MAX_LENGTH) : errorCode;
    }

    private <T> T inTransaction(Supplier<T> work) {
        return new TransactionTemplate(transactionManager).execute(status -> work.get());
    }
}
