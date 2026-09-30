package DGU_AI_LAB.admin_be.domain.scheduler;

import DGU_AI_LAB.admin_be.domain.alarm.service.AlarmService;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.users.entity.Role;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import DGU_AI_LAB.admin_be.global.util.MessageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

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
    private final RedisTemplate<String, Object> redisTemplate;

    /** 컨테이너 없이 이만큼 접속하지 않으면 비활성화한다. */
    static final int INACTIVE_MONTHS = 3;

    /**
     * 장기 미접속 규칙 — 컨테이너가 하나도 없는 채로 마지막 활동에서 3개월이 지나면 비활성화한다.
     * <ul>
     *   <li>끝나지 않은 신청(대기·처리 중·사용 중·이동 중·회수 중)이 하나라도 있으면 대상이 아니다.</li>
     *   <li>마지막 활동 = 마지막 접속(없으면 가입 시각)과 마지막 컨테이너가 사라진 시각 중 늦은 쪽.</li>
     * </ul>
     * 즉 "컨테이너가 0개가 된 지 3개월"과 "마지막 접속 3개월"을 둘 다 넘어야 한다. 비활성화 7·3·1일 전에 경고한다.
     * 비활성화(컨테이너·우분투 계정 회수 포함)는 HTTP 호출이 있어 이 트랜잭션 밖에서 호출자가 수행한다.
     *
     * @return 비활성화 예정일이 지나 비활성화해야 하면 true
     */
    @Transactional
    public boolean processInactiveUser(Long userId, LocalDateTime now) {
        User user = userRepository.findById(userId).orElseThrow();
        // 조회 뒤 관리자로 승격됐거나 새로 신청했을 수 있어 트랜잭션 안에서 다시 본다.
        if (!user.getIsActive() || user.getRole() == Role.ADMIN || hasOpenRequest(user)) {
            return false;
        }

        LocalDateTime lastActivity = lastActivity(user);
        if (lastActivity == null) {
            log.warn("유저({}) 활동 기준 시각이 없어 수명주기 판정을 건너뜁니다", userId);
            return false;
        }

        LocalDateTime deleteDate = lastActivity.plusMonths(INACTIVE_MONTHS);
        long daysLeft = ChronoUnit.DAYS.between(now.toLocalDate(), deleteDate.toLocalDate());

        if (daysLeft == 7 || daysLeft == 3 || daysLeft == 1) {
            sendWarningAlert(user, daysLeft, deleteDate, now.toLocalDate().toString());
        }
        return daysLeft <= 0;
    }

    private static boolean hasOpenRequest(User user) {
        return user.getRequests().stream().anyMatch(r -> Status.openStatuses().contains(r.getStatus()));
    }

    /**
     * 마지막 접속(없으면 가입 시각)과 마지막 컨테이너가 사라진 시각 중 늦은 쪽. 컨테이너가 사라진 시각은 승인됐던
     * 신청이 DELETED로 끝난 시각(updated_at)이다 — DELETED는 끝 상태라 그 뒤로 행이 바뀌지 않는다.
     */
    private static LocalDateTime lastActivity(User user) {
        LocalDateTime lastSeen = user.getLastLoginAt() != null ? user.getLastLoginAt() : user.getCreatedAt();
        LocalDateTime lastContainerGone = user.getRequests().stream()
                .filter(r -> r.getStatus() == Status.DELETED && r.getApprovedAt() != null)
                .map(Request::getUpdatedAt)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);
        if (lastSeen == null || lastContainerGone == null) {
            return lastSeen != null ? lastSeen : lastContainerGone;
        }
        return lastContainerGone.isAfter(lastSeen) ? lastContainerGone : lastSeen;
    }

    private void sendWarningAlert(User user, long daysLeft, LocalDateTime deleteDate, String today) {
        if (isDuplicateWarning(user.getUserId(), daysLeft, today)) {
            log.debug("계정 삭제 경고 중복 발송 방지: userId={}, daysLeft={}, date={}", user.getUserId(), daysLeft, today);
            return;
        }

        String dateStr = deleteDate.toLocalDate().toString();
        String subject = messageUtils.get("notification.user.delete-warning.subject", String.valueOf(daysLeft));
        String body = messageUtils.get("notification.user.delete-warning.body",
                user.getName(), String.valueOf(daysLeft), dateStr);

        alarmService.sendAllAlerts(user.getName(), user.getEmail(), subject, body);
        log.info("경고 알림 발송: {} ({}일 전)", user.getEmail(), daysLeft);
    }

    /**
     * Redis SETNX로 당일 중복 발송을 방지한다 (RequestNotificationService.isDuplicate와 동일 패턴).
     * Redis 장애 시 false를 반환해 이메일 발송을 허용한다(fail-open).
     */
    private boolean isDuplicateWarning(Long userId, long daysLeft, String date) {
        try {
            String key = "email:user-inactive-warning:" + userId + ":" + daysLeft + ":" + date;
            Boolean set = redisTemplate.opsForValue().setIfAbsent(key, "sent", Duration.ofHours(25));
            return Boolean.FALSE.equals(set);
        } catch (Exception e) {
            log.warn("Redis 중복 체크 실패, 발송 진행: {}", e.getMessage());
            return false;
        }
    }
}
