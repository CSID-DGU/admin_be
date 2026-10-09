package DGU_AI_LAB.admin_be.domain.warnings.service;

import DGU_AI_LAB.admin_be.domain.alarm.service.AlarmService;
import DGU_AI_LAB.admin_be.domain.users.entity.Role;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import DGU_AI_LAB.admin_be.domain.warnings.dto.WarningStatusResponseDTO;
import DGU_AI_LAB.admin_be.domain.warnings.entity.UserSuspension;
import DGU_AI_LAB.admin_be.domain.warnings.entity.UserWarning;
import DGU_AI_LAB.admin_be.domain.warnings.policy.WarningLedger;
import DGU_AI_LAB.admin_be.domain.warnings.policy.WarningPolicy;
import DGU_AI_LAB.admin_be.domain.warnings.repository.UserSuspensionRepository;
import DGU_AI_LAB.admin_be.domain.warnings.repository.UserWarningRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import DGU_AI_LAB.admin_be.global.util.AfterCommit;
import DGU_AI_LAB.admin_be.global.util.MessageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 경고 부여·차감·취소와, 그에 따른 이용 정지.
 *
 * <p>경고와 정지는 DB 에만 적고 돌아온다. 실제 접속 차단·해제는 {@link AccessEnforcementService}가 정지 여부를 보고
 * 맞춘다 — 여기서는 커밋한 뒤 한 번 불러 주기만 하고, 그 호출이 실패해도 경고는 남는다(다음 점검 때 다시 맞춘다).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WarningService {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final UserWarningRepository warningRepository;
    private final UserSuspensionRepository suspensionRepository;
    private final UserRepository userRepository;
    private final AccessEnforcementService accessEnforcementService;
    private final AlarmService alarmService;
    private final MessageUtils messageUtils;
    private final PlatformTransactionManager transactionManager;

    /** 커밋한 뒤 사용자에게 보낼 안내. */
    private record Notice(String name, String email, String subject, String body) {}

    private record Outcome(WarningStatusResponseDTO status, Notice notice) {}

    /** 경고를 하나 준다. 횟수가 정지 기준에 닿으면 지금부터 이용 정지가 시작된다. */
    public WarningStatusResponseDTO grant(Long adminId, Long userId, String reason) {
        return apply(userId, "경고 부여", () -> {
            User user = lockTarget(userId);
            if (user.getRole() == Role.ADMIN) {
                throw new BusinessException(ErrorCode.WARNING_TARGET_IS_ADMIN);
            }
            LocalDateTime now = LocalDateTime.now();
            int count = WarningLedger.replay(rowsOf(userId)).count() + 1;
            UserWarning warning = warningRepository.save(UserWarning.grant(user, admin(adminId), reason));
            int days = WarningPolicy.suspensionDays(count);
            String body;
            if (days > 0) {
                UserSuspension suspension = suspensionRepository.save(new UserSuspension(warning, now, days));
                body = messageUtils.get("notification.user.warning.suspended.body", user.getName(), reason,
                        String.valueOf(count), String.valueOf(days), suspension.getEndsAt().format(TIME_FORMAT));
            } else {
                body = messageUtils.get("notification.user.warning.granted.body", user.getName(), reason,
                        String.valueOf(count));
            }
            log.info("[warning] 경고 부여: userId={}, count={}, suspensionDays={}, adminId={}",
                    userId, count, days, adminId);
            return notice(user, "notification.user.warning.granted.subject", body);
        });
    }

    /** 절차를 지킨 신고에 따라 경고를 하나 뺀다. 경고가 3회 이상 쌓인 적이 있어야 한다. 정지에는 영향이 없다. */
    public WarningStatusResponseDTO deduct(Long adminId, Long userId, String reason) {
        return apply(userId, "경고 차감", () -> {
            User user = lockTarget(userId);
            WarningLedger ledger = WarningLedger.replay(rowsOf(userId));
            if (!ledger.deductible()) {
                throw new BusinessException(ErrorCode.WARNING_NOT_DEDUCTIBLE);
            }
            warningRepository.save(UserWarning.deduct(user, admin(adminId), reason));
            log.info("[warning] 경고 차감: userId={}, count={}, adminId={}", userId, ledger.count() - 1, adminId);
            return notice(user, "notification.user.warning.reduced.subject",
                    messageUtils.get("notification.user.warning.reduced.body", user.getName(), reason,
                            String.valueOf(ledger.count() - 1)));
        });
    }

    /** 잘못 준 경고를 취소한다. 그 경고로 시작된 이용 정지가 아직 끝나지 않았으면 지금 끝낸다. */
    public WarningStatusResponseDTO cancel(Long adminId, Long userId, Long warningId, String reason) {
        return apply(userId, "경고 취소", () -> {
            User user = lockTarget(userId);
            List<UserWarning> rows = rowsOf(userId);
            UserWarning granted = rows.stream()
                    .filter(row -> row.getWarningId().equals(warningId) && row.isGrant())
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(ErrorCode.WARNING_NOT_FOUND));
            if (WarningLedger.canceledWarningIds(rows).contains(warningId)) {
                throw new BusinessException(ErrorCode.WARNING_ALREADY_CANCELED);
            }
            warningRepository.save(UserWarning.cancel(granted, admin(adminId), reason));
            suspensionRepository.findByWarning_WarningId(warningId)
                    .ifPresent(suspension -> suspension.endNow(LocalDateTime.now()));
            int count = WarningLedger.replay(rowsOf(userId)).count();
            log.info("[warning] 경고 취소: userId={}, warningId={}, count={}, adminId={}",
                    userId, warningId, count, adminId);
            return notice(user, "notification.user.warning.reduced.subject",
                    messageUtils.get("notification.user.warning.reduced.body", user.getName(), reason,
                            String.valueOf(count)));
        });
    }

    /** 관리자가 보는 경고 현황. */
    public WarningStatusResponseDTO getStatus(Long userId) {
        return inTransaction(() -> {
            if (!userRepository.existsById(userId)) {
                throw new BusinessException(ErrorCode.USER_NOT_FOUND);
            }
            return statusOf(userId, true);
        });
    }

    /** 본인이 보는 경고 현황. 처리한 관리자 이름은 싣지 않는다. */
    public WarningStatusResponseDTO getOwnStatus(Long userId) {
        return inTransaction(() -> statusOf(userId, false));
    }

    /** 대장을 바꾸고, 커밋한 뒤 접속을 맞추고 사용자에게 알린다. */
    private WarningStatusResponseDTO apply(Long userId, String what, Supplier<Notice> change) {
        Outcome outcome = inTransaction(() -> {
            Notice notice = change.get();
            return new Outcome(statusOf(userId, true), notice);
        });
        try {
            accessEnforcementService.enforce(userId);
        } catch (Exception e) {
            // 경고는 이미 남았다. 접속은 다음 점검 때 다시 맞춘다.
            log.error("[warning] {} 뒤 접속 상태를 맞추지 못함 - 다음 점검 때 다시 맞춘다: userId={}", what, userId, e);
        }
        Notice notice = outcome.notice();
        AfterCommit.run(what + " 안내, userId " + userId,
                () -> alarmService.notifyUser(notice.name(), notice.email(), notice.subject(), notice.body()));
        return outcome.status();
    }

    private WarningStatusResponseDTO statusOf(Long userId, boolean withIssuer) {
        List<UserWarning> rows = rowsOf(userId);
        WarningLedger ledger = WarningLedger.replay(rows);
        Set<Long> canceledIds = WarningLedger.canceledWarningIds(rows);
        LocalDateTime suspendedUntil = suspensionRepository
                .findFirstByUser_UserIdAndEndsAtAfterOrderByEndsAtDesc(userId, LocalDateTime.now())
                .map(UserSuspension::getEndsAt).orElse(null);
        List<WarningStatusResponseDTO.Entry> history = rows.stream()
                .sorted(Comparator.comparing(UserWarning::getWarningId).reversed())
                .map(row -> new WarningStatusResponseDTO.Entry(
                        row.getWarningId(), row.getType(), row.getReason(),
                        withIssuer ? row.getIssuedBy().getName() : null,
                        row.getCreatedAt(),
                        canceledIds.contains(row.getWarningId()),
                        row.getCanceledWarning() == null ? null : row.getCanceledWarning().getWarningId()))
                .toList();
        return new WarningStatusResponseDTO(ledger.count(), ledger.deductible(), ledger.nextSuspensionDays(),
                suspendedUntil, history);
    }

    /** 같은 사용자의 경고 처리를 사용자 행 잠금으로 줄 세운다 — 동시에 준 두 경고가 같은 횟수로 계산되지 않게 한다. */
    private User lockTarget(Long userId) {
        return userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private User admin(Long adminId) {
        return userRepository.findById(adminId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private List<UserWarning> rowsOf(Long userId) {
        return warningRepository.findAllByUser_UserIdOrderByWarningIdAsc(userId);
    }

    private Notice notice(User user, String subjectKey, String body) {
        return new Notice(user.getName(), user.getEmail(), messageUtils.get(subjectKey), body);
    }

    private <T> T inTransaction(Supplier<T> work) {
        return new TransactionTemplate(transactionManager).execute(status -> work.get());
    }
}
