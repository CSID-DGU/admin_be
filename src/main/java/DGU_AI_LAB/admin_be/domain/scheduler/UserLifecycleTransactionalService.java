package DGU_AI_LAB.admin_be.domain.scheduler;

import DGU_AI_LAB.admin_be.domain.alarm.service.AlarmService;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
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

    private static final int INACTIVE_MONTHS = 3;

    /**
     * 특정 유저의 비활성 여부를 판단해 경고 알림을 보내고, 탈퇴 대상인지 돌려준다.
     * 탈퇴(컨테이너·우분투 계정 회수 포함)는 HTTP 호출이 있어 이 트랜잭션 밖에서 호출자가 수행한다.
     * 유저별 독립 트랜잭션으로 실행되어, 한 유저 실패가 다른 유저 처리에 영향을 주지 않습니다.
     *
     * @return 삭제 예정일이 지나 탈퇴시켜야 하면 true
     */
    @Transactional
    public boolean processInactiveUser(Long userId, LocalDateTime now) {
        User user = userRepository.findById(userId).orElseThrow();
        if (!user.getIsActive()) {
            return false;
        }
        // 조회 뒤 관리자로 승격됐을 수 있어 트랜잭션 안에서 다시 본다.
        if (user.getRole() == Role.ADMIN) {
            return false;
        }

        // 로그인 기록이 없는 유저(로그인 시각 도입 전 가입)는 가입 시각부터 센다.
        LocalDateTime lastActivity = user.getLastLoginAt() != null ? user.getLastLoginAt() : user.getCreatedAt();

        // Lazy 컬렉션을 트랜잭션 내에서 접근 (H-7 fix)
        LocalDateTime lastPodExpire = user.getRequests().stream()
                .map(Request::getExpiresAt)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);
        if (lastPodExpire != null && (lastActivity == null || lastPodExpire.isAfter(lastActivity))) {
            lastActivity = lastPodExpire;
        }
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
