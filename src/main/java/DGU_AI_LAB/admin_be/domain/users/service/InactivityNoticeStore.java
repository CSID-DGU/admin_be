package DGU_AI_LAB.admin_be.domain.users.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * 장기 미사용 사용자에게 경고하면서 알린 비활성화 예정일을 Redis에 남긴다. 예정일이 기록된 사용자만 비활성화하므로,
 * 경고를 한 번도 받지 못한 사용자가 바로 비활성화되지 않는다.
 *
 * <p>기록은 예정일이 지나고 며칠 뒤 사라진다. 그 사이 비활성화가 실패해 다음 날 다시 시도하는 동안에는 남아 있고,
 * 오래된 기록이 다음 미사용 주기의 판정에 쓰이지는 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InactivityNoticeStore {

    static final String KEY_PREFIX = "user-inactive-notice:";
    static final int KEEP_DAYS_AFTER_DEADLINE = 3;

    private final StringRedisTemplate redis;

    /**
     * 이 사용자에게 알린 비활성화 예정일. 알린 적이 없으면 빈 값.
     *
     * <p>Redis에 닿지 못하면 예외를 그대로 던진다 — 알렸는지 모르는 채로 비활성화하지 않게 호출자가 그 회차를 건너뛴다.
     */
    public Optional<LocalDate> findDeadline(Long userId) {
        return Optional.ofNullable(redis.opsForValue().get(KEY_PREFIX + userId)).map(LocalDate::parse);
    }

    /**
     * 경고와 함께 알린 예정일을 남긴다.
     *
     * @return 남겼으면 true. false면 기록이 없으므로 호출자는 이 예정일을 알린 것으로 치면 안 된다
     */
    public boolean save(Long userId, LocalDate deadline, LocalDate today) {
        try {
            long keepDays = Math.max(ChronoUnit.DAYS.between(today, deadline), 0) + KEEP_DAYS_AFTER_DEADLINE;
            redis.opsForValue().set(KEY_PREFIX + userId, deadline.toString(), Duration.ofDays(keepDays));
            return true;
        } catch (Exception e) {
            log.warn("비활성화 예정일을 기록하지 못함: userId={}", userId, e);
            return false;
        }
    }

    /**
     * 다시 활성화되는 사용자의 기록을 지운다. 어느 경로(자동·관리자)로 비활성화됐든 기록이 남아 있으면, 다시 활성화된
     * 사용자가 옛 예정일로 경고 없이 비활성화된다.
     *
     * <p>Redis에 닿지 못하면 예외를 그대로 던진다 — 기록이 남은 채로 다시 활성화하지 않게 호출자가 그 요청을 실패시킨다.
     */
    public void forget(Long userId) {
        redis.delete(KEY_PREFIX + userId);
    }
}
