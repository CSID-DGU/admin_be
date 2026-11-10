package DGU_AI_LAB.admin_be.domain.scheduler;

import DGU_AI_LAB.admin_be.domain.alarm.service.AlarmService;
import DGU_AI_LAB.admin_be.domain.home.service.HomeRetentionPolicy;
import DGU_AI_LAB.admin_be.domain.users.entity.Role;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import DGU_AI_LAB.admin_be.domain.users.service.InactivityNoticeStore;
import DGU_AI_LAB.admin_be.global.util.AfterCommit;
import DGU_AI_LAB.admin_be.global.util.EmailAddresses;
import DGU_AI_LAB.admin_be.global.util.MessageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
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
    private final RedisTemplate<String, Object> redisTemplate;
    private final InactivityNoticeStore noticeStore;
    private final HomeRetentionPolicy homeRetentionPolicy;

    /** 마지막 컨테이너가 끝난 뒤(컨테이너를 쓴 적이 없으면 가입한 뒤) 이만큼 지나면 비활성화한다. */
    static final int INACTIVE_MONTHS = 12;

    /** 졸업 예정일로부터 이만큼 지나면 비활성화한다. 1년 미사용 규칙과 함께 적용하고 더 이른 쪽을 따른다. */
    static final int GRADUATION_GRACE_DAYS = 30;

    /** 기한이 지나도록 경고를 받지 못한 사용자에게 경고와 함께 주는 유예 기간. */
    static final int NOTICE_DAYS = 7;

    /**
     * 장기 미사용 규칙 — 마지막 컨테이너가 끝난 지 1년이 지나면 비활성화한다. 로그인 여부는 보지 않는다.
     * <ul>
     *   <li>끝나지 않은 신청(대기·처리 중·사용 중·이동 중·회수 중)이 하나라도 있으면 대상이 아니다.</li>
     *   <li>기준 시각 = 마지막 컨테이너가 끝난 시각. 컨테이너를 쓴 적이 없으면 가입 시각.</li>
     * </ul>
     * 비활성화 7·3·1일 전에 경고한다. 기한이 지났는데 경고한 적이 없는 사용자(오래된 계정이 다시 활성화됐거나, 오래
     * 기다리던 신청이 거절돼 대상이 된 경우)는 바로 비활성화하지 않고, 그날 경고하면서 {@link #NOTICE_DAYS}일 뒤로
     * 예정일을 잡는다. 비활성화(컨테이너·우분투 계정 회수 포함)는 HTTP 호출이 있어 이 트랜잭션 밖에서 호출자가 수행한다.
     *
     * @return 비활성화 예정일이 지나 비활성화해야 하면 true
     */
    @Transactional
    public boolean processInactiveUser(Long userId, LocalDateTime now) {
        User user = userRepository.findById(userId).orElseThrow();
        // 조회 뒤 관리자로 승격됐거나 새로 신청했을 수 있어 트랜잭션 안에서 다시 본다.
        if (!user.getIsActive() || user.getRole() == Role.ADMIN || user.hasOpenRequest()) {
            return false;
        }

        LocalDateTime inactiveSince = inactiveSince(user);
        if (inactiveSince == null) {
            log.warn("유저({}) 미사용 기준 시각이 없어 수명주기 판정을 건너뜁니다", userId);
            return false;
        }

        LocalDate today = now.toLocalDate();
        RuleDeadline rule = ruleDeadline(user);
        LocalDate ruleDate = rule.date();
        boolean byGraduation = rule.byGraduation();
        LocalDate deleteDate = ruleDate.isAfter(today) ? ruleDate : noticedDeadline(userId, today);
        long daysLeft = ChronoUnit.DAYS.between(today, deleteDate);

        if (daysLeft == 7 || daysLeft == 3 || daysLeft == 1) {
            boolean recorded = noticeStore.save(userId, deleteDate, today);
            if (!recorded && !ruleDate.isAfter(today)) {
                // 유예는 기록된 예정일로만 센다. 기록 없이 경고하면 내일 또 새 유예로 경고가 나가고 비활성화는 오지 않는다.
                log.warn("유저({}) 유예 예정일을 기록하지 못해 경고를 보내지 않고 다음 회차에 다시 봅니다", userId);
                return false;
            }
            sendWarningAlert(user, daysLeft, deleteDate, today.toString(), byGraduation);
        }
        return daysLeft <= 0;
    }

    /**
     * 두 규칙(졸업 예정일 + {@link #GRADUATION_GRACE_DAYS}, 마지막 컨테이너 종료 + {@link #INACTIVE_MONTHS})의 기한과
     * 어느 쪽이 이른지. 졸업 예정일을 계속 미뤄도 1년 미사용 규칙은 피하지 못하므로 더 이른 쪽을 쓴다.
     * 판정은 이 메서드만 한다 — 스케줄러 경고·비활성화와 관리자 탈퇴가 같은 결과를 쓴다.
     * 미사용 기준 시각이 없으면(가입 시각도 없는 미저장 엔티티) 졸업 기한만 본다.
     */
    public static RuleDeadline ruleDeadline(User user) {
        LocalDateTime inactiveSince = inactiveSince(user);
        LocalDate inactivityDate = inactiveSince == null ? null : inactiveSince.plusMonths(INACTIVE_MONTHS).toLocalDate();
        LocalDate graduationDate = user.getExpectedGraduationDate() == null
                ? null : user.getExpectedGraduationDate().plusDays(GRADUATION_GRACE_DAYS);
        boolean byGraduation = graduationDate != null
                && (inactivityDate == null || graduationDate.isBefore(inactivityDate));
        return new RuleDeadline(byGraduation ? graduationDate : inactivityDate, byGraduation);
    }

    /** 규칙별 기한 중 적용되는 기한과, 그것이 졸업 규칙인지. */
    public record RuleDeadline(LocalDate date, boolean byGraduation) {
    }

    /** 규칙상 기한이 지난 사용자의 비활성화 예정일. 경고하면서 알린 날이 있으면 그날, 없으면 오늘부터 유예를 준다. */
    private LocalDate noticedDeadline(Long userId, LocalDate today) {
        return noticeStore.findDeadline(userId).orElseGet(() -> today.plusDays(NOTICE_DAYS));
    }

    /**
     * 마지막 컨테이너가 끝난 시각({@link User#lastContainerEndedAt()}). 컨테이너를 쓴 적이 없으면 가입 시각. 어느 쪽이든
     * 가입 시각보다 이르지 않다(UserRepository.findInactiveUsers가 가입 시각으로 후보를 추리는 근거).
     */
    private static LocalDateTime inactiveSince(User user) {
        return user.lastContainerEndedAt().orElse(user.getCreatedAt());
    }

    private void sendWarningAlert(User user, long daysLeft, LocalDate deleteDate, String today, boolean byGraduation) {
        if (isDuplicateWarning(user.getUserId(), daysLeft, today)) {
            log.debug("계정 삭제 경고 중복 발송 방지: userId={}, daysLeft={}, date={}", user.getUserId(), daysLeft, today);
            return;
        }

        String dateStr = deleteDate.toString();
        String noticeKey = byGraduation ? "notification.user.graduation-warning" : "notification.user.delete-warning";
        String subject = messageUtils.get(noticeKey + ".subject", String.valueOf(daysLeft));
        String body = messageUtils.get(noticeKey + ".body",
                user.getName(), String.valueOf(daysLeft), dateStr, String.valueOf(homeRetentionPolicy.announcedDays()));

        String name = user.getName();
        String email = user.getEmail();
        // 문구는 지금 만들고 전송은 커밋 뒤에 한다 — 메일 서버가 느려도 트랜잭션을 그만큼 열어 두지 않는다.
        AfterCommit.run("계정 비활성화 경고, userId " + user.getUserId(), () -> {
            alarmService.notifyUser(name, email, subject, body);
            log.info("경고 알림 발송: {} ({}일 전)", EmailAddresses.mask(email), daysLeft);
        });
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
