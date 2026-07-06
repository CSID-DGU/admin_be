package DGU_AI_LAB.admin_be.domain.scheduler;

import DGU_AI_LAB.admin_be.domain.alarm.service.AlarmService;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import DGU_AI_LAB.admin_be.global.util.MessageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 유저 생명주기 처리를 트랜잭션 경계 안에서 실행합니다.
 * UserSchedulerService의 self-call 한계를 우회하고, 유저별 독립 트랜잭션을 보장합니다.
 * (H-7: LazyInitializationException 방지, H-11: 단일 트랜잭션으로 인한 롤백 전파 방지)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserLifecycleTransactionalService {

    private final UserRepository userRepository;
    private final AlarmService alarmService;
    private final MessageUtils messageUtils;

    private static final int INACTIVE_MONTHS = 3;

    /**
     * 특정 유저의 비활성 여부를 판단하고, 경고 알림 발송 또는 Soft Delete를 수행합니다.
     * 유저별 독립 트랜잭션으로 실행되어, 한 유저 실패가 다른 유저 처리에 영향을 주지 않습니다.
     */
    @Transactional
    public void processInactiveUser(Long userId, LocalDateTime now) {
        User user = userRepository.findById(userId).orElseThrow();

        LocalDateTime lastActivity = user.getLastLoginAt();

        // Lazy 컬렉션을 트랜잭션 내에서 접근 (H-7 fix)
        if (!user.getRequests().isEmpty()) {
            LocalDateTime lastPodExpire = user.getRequests().stream()
                    .map(req -> req.getExpiresAt())
                    .max(LocalDateTime::compareTo)
                    .orElse(LocalDateTime.MIN);

            if (lastPodExpire.isAfter(lastActivity)) {
                lastActivity = lastPodExpire;
            }
        }

        LocalDateTime deleteDate = lastActivity.plusMonths(INACTIVE_MONTHS);
        long daysLeft = ChronoUnit.DAYS.between(now.toLocalDate(), deleteDate.toLocalDate());

        if (daysLeft == 7 || daysLeft == 3 || daysLeft == 1) {
            sendWarningAlert(user, daysLeft, deleteDate);
        } else if (daysLeft <= 0) {
            softDeleteUser(user);
        }
    }

    /**
     * 특정 유저를 DB에서 완전 삭제(Hard Delete)합니다.
     * 독립 트랜잭션으로 실행되어, 실패 시 다른 유저의 처리에 영향을 주지 않습니다.
     */
    @Transactional
    public void hardDeleteUser(Long userId, String email) {
        userRepository.findById(userId).ifPresent(user -> {
            userRepository.delete(user);
            log.info("계정 영구 삭제(Hard Delete) 완료: ID={}, Email={}", userId, email);
        });
    }

    private void sendWarningAlert(User user, long daysLeft, LocalDateTime deleteDate) {
        String dateStr = deleteDate.toLocalDate().toString();
        String subject = messageUtils.get("notification.user.delete-warning.subject", String.valueOf(daysLeft));
        String body = messageUtils.get("notification.user.delete-warning.body",
                user.getName(), String.valueOf(daysLeft), dateStr);

        alarmService.sendAllAlerts(user.getName(), user.getEmail(), subject, body);
        log.info("경고 알림 발송: {} ({}일 전)", user.getEmail(), daysLeft);
    }

    private void softDeleteUser(User user) {
        user.withdraw();

        String subject = messageUtils.get("notification.user.soft-delete.subject");
        String body = messageUtils.get("notification.user.soft-delete.body", user.getName());

        alarmService.sendAllAlerts(user.getName(), user.getEmail(), subject, body);
        log.info("계정 비활성화(Soft Delete) 완료: {}", user.getEmail());
    }
}
