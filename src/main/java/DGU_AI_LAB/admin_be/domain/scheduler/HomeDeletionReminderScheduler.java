package DGU_AI_LAB.admin_be.domain.scheduler;

import DGU_AI_LAB.admin_be.domain.alarm.service.AlarmService;
import DGU_AI_LAB.admin_be.domain.home.service.HomeRetentionPolicy;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import DGU_AI_LAB.admin_be.global.util.MessageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * 사용자에게 안내한 홈 삭제일의 7일 전과 1일 전에 메일·Slack DM으로 다시 알린다. 삭제일은 컨테이너가 끝날 때 한 번
 * 안내했지만, 한 달 가까이 지나 잊기 쉽다. 실제 삭제는 안내한 날보다 늦으므로({@link HomeRetentionPolicy}) 이 안내를
 * 받고 새로 신청할 시간이 있다. 삭제 작업({@link HomeCleanupScheduler})보다 먼저 돈다.
 *
 * <p>하루에 한 번 도는 구조라 "24시간 전"은 "전날"로 센다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HomeDeletionReminderScheduler {

    /** 삭제일까지 남은 일수 중 안내를 보내는 날. */
    static final List<Integer> REMIND_DAYS_BEFORE = List.of(7, 1);

    private final UserRepository userRepository;
    private final HomeRetentionPolicy retentionPolicy;
    private final AlarmService alarmService;
    private final MessageUtils messageUtils;
    private final RedisTemplate<String, Object> redisTemplate;

    @Scheduled(cron = "${home-retention.reminder-cron:0 0 9 * * ?}", zone = "Asia/Seoul")
    public void run() {
        remind(LocalDate.now(ZoneId.of("Asia/Seoul")));
    }

    void remind(LocalDate today) {
        for (int daysLeft : REMIND_DAYS_BEFORE) {
            LocalDate deletionDate = today.plusDays(daysLeft);
            LocalDate endedOn = retentionPolicy.containerEndDateFor(deletionDate);
            List<User> users = userRepository.findHomeReminderCandidates(
                    endedOn.atStartOfDay(), endedOn.plusDays(1).atStartOfDay(), Status.openStatuses());
            for (User user : users) {
                try {
                    remind(user, daysLeft, deletionDate, today);
                } catch (Exception e) {
                    // 한 사용자의 실패가 나머지를 막지 않게 한다. 다음 날은 날짜가 달라 같은 안내를 다시 보내지 않는다.
                    log.error("[homeReminder] 홈 삭제 안내 실패: userId={}, daysLeft={}", user.getUserId(), daysLeft, e);
                }
            }
        }
    }

    private void remind(User user, int daysLeft, LocalDate deletionDate, LocalDate today) {
        if (isDuplicate(user.getUserId(), daysLeft, today)) {
            return;
        }
        String subject = messageUtils.get("notification.user.home-delete-reminder.subject", String.valueOf(daysLeft));
        String body = messageUtils.get("notification.user.home-delete-reminder.body",
                user.getName(), user.getUbuntuUsername(), String.valueOf(daysLeft), deletionDate.toString());
        alarmService.notifyUser(user.getName(), user.getEmail(), subject, body);
    }

    /** 같은 날 다시 도는 경우를 막는다. Redis 장애 시에는 보내는 쪽으로 열어 둔다(fail-open). */
    private boolean isDuplicate(Long userId, int daysLeft, LocalDate today) {
        try {
            String key = "email:home-delete-reminder:" + userId + ":" + daysLeft + ":" + today;
            return Boolean.FALSE.equals(redisTemplate.opsForValue().setIfAbsent(key, "sent", Duration.ofHours(25)));
        } catch (Exception e) {
            log.warn("[homeReminder] Redis 중복 확인 실패, 발송 진행: {}", e.getMessage());
            return false;
        }
    }
}
