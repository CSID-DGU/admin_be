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
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * 사용자에게 안내한 홈 삭제일의 7일 전과 1일 전에 메일·Slack DM으로 다시 알린다. 삭제일은 컨테이너가 끝날 때 한 번
 * 안내했지만, 한 달 가까이 지나 잊기 쉽다. 실제 삭제는 안내한 날보다 늦으므로({@link HomeRetentionPolicy}) 이 안내를
 * 받고 새로 신청할 시간이 있다. 삭제 작업({@link HomeCleanupScheduler})보다 먼저 돈다.
 *
 * <p>대상은 "삭제일이 오늘 뒤 7일 안에 든 계정"이다 — 실행이 며칠 빠져도 창 안에 남아 있으면 다음 실행에서 따라잡는다.
 * 한 사용자는 하루에 한 단계만 받는다(남은 날이 2~7일이면 7일 단계, 1일이면 1일 단계). 삭제일 당일 이후는 보내지 않는다.
 * 같은 주기·같은 단계는 Redis 키로 한 번만 보내고, 발송이 실패하면 키를 풀어 다음 실행에서 다시 보낸다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HomeDeletionReminderScheduler {

    /** 삭제일까지 남은 일수 중 안내를 보내는 단계. 큰 값부터 쓴다. */
    static final List<Integer> REMIND_DAYS_BEFORE = List.of(7, 1);

    /** 한 삭제 주기 동안 같은 단계를 막을 만큼 둔다(창이 닫힌 뒤에도 중복되지 않게). */
    private static final Duration CLAIM_TTL = Duration.ofDays(60);

    private final UserRepository userRepository;
    private final HomeRetentionPolicy retentionPolicy;
    private final AlarmService alarmService;
    private final MessageUtils messageUtils;
    private final RedisTemplate<String, Object> redisTemplate;

    // 마지막 컨테이너 종료 시각이 신청 목록(지연 로딩)을 읽으므로 실행 전체를 읽기 트랜잭션으로 묶는다.
    @Transactional(readOnly = true)
    @Scheduled(cron = "${home-retention.reminder-cron:0 0 9 * * ?}", zone = "Asia/Seoul")
    public void run() {
        remind(LocalDate.now(ZoneId.of("Asia/Seoul")));
    }

    void remind(LocalDate today) {
        int window = REMIND_DAYS_BEFORE.stream().mapToInt(Integer::intValue).max().orElseThrow();
        // 삭제일 today+1 ~ today+window 에 해당하는 마지막 컨테이너 종료일 범위를 시각으로 바꾼다.
        LocalDate firstEnded = retentionPolicy.containerEndDateFor(today.plusDays(1));
        LocalDate lastEnded = retentionPolicy.containerEndDateFor(today.plusDays(window));
        List<User> users = userRepository.findHomeReminderCandidates(
                firstEnded.atStartOfDay(), lastEnded.plusDays(1).atStartOfDay(), Status.openStatuses(), User.PRIMARY_SERVER);
        for (User user : users) {
            try {
                remind(user, today);
            } catch (Exception e) {
                // 한 사용자의 실패가 나머지를 막지 않게 한다. 키는 실패 시 풀려 있어 다음 실행에서 다시 본다.
                log.error("[homeReminder] 홈 삭제 안내 실패: userId={}", user.getUserId(), e);
            }
        }
    }

    private void remind(User user, LocalDate today) {
        Optional<LocalDateTime> endedAt = user.lastContainerEndedAt(User.PRIMARY_SERVER);
        if (endedAt.isEmpty()) {
            return;
        }
        LocalDate deletionDate = retentionPolicy.announcedDeletionDate(endedAt.get().toLocalDate());
        long daysLeft = ChronoUnit.DAYS.between(today, deletionDate);
        if (daysLeft < 1 || daysLeft > REMIND_DAYS_BEFORE.get(0)) {
            return;
        }
        String key = "email:home-delete-reminder:" + user.getUserId() + ":" + stageOf(daysLeft) + ":" + deletionDate;
        if (isDuplicate(key)) {
            return;
        }
        boolean sent;
        try {
            String subject = messageUtils.get("notification.user.home-delete-reminder.subject", String.valueOf(daysLeft));
            String body = messageUtils.get("notification.user.home-delete-reminder.body",
                    user.getName(), user.getUbuntuUsername(), String.valueOf(daysLeft), deletionDate.toString());
            sent = alarmService.notifyUser(user.getName(), user.getEmail(), subject, body);
        } catch (RuntimeException e) {
            release(key);
            throw e;
        }
        if (!sent) {
            release(key);
            log.warn("[homeReminder] 메일 발송 실패, 다음 실행에서 다시 보냅니다: userId={}", user.getUserId());
        }
    }

    /** 남은 일수를 덮는 가장 가까운 단계(예: 5일 → 7일 단계, 1일 → 1일 단계). */
    private static int stageOf(long daysLeft) {
        return REMIND_DAYS_BEFORE.stream()
                .filter(stage -> daysLeft <= stage)
                .min(Integer::compare)
                .orElseThrow();
    }

    /**
     * 같은 주기·같은 단계가 이미 잡혀 있으면 true. 없으면 지금 잡고 false — 발송이 실패하면 {@link #release}로 푼다.
     * Redis 장애 시에는 보내는 쪽으로 열어 둔다(fail-open).
     */
    private boolean isDuplicate(String key) {
        try {
            return Boolean.FALSE.equals(redisTemplate.opsForValue().setIfAbsent(key, "sent", CLAIM_TTL));
        } catch (Exception e) {
            log.warn("[homeReminder] Redis 중복 확인 실패, 발송 진행: {}", e.getMessage());
            return false;
        }
    }

    private void release(String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("[homeReminder] 중복 키 해제 실패(다음 실행에서 이 단계가 빠질 수 있음): {}", e.getMessage());
        }
    }
}
