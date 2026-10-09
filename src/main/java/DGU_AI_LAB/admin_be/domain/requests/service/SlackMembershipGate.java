package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.domain.alarm.service.SlackApiService;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 컨테이너 신청을 받기 전에 신청자가 Slack 워크스페이스 회원인지 본다. 승인·만료 안내가 Slack DM으로 가므로,
 * 가입하지 않은 사람은 안내를 받지 못한다. 회원 정보의 이름과 Slack 이름이 같아야 같은 사람으로 본다 — DM을 보낼
 * 사람을 찾는 규칙과 같다.
 *
 * <p>{@code slack.membership-check.enabled}를 켠 환경에서만 막는다(기본값 false). Slack을 조회하지 못하면 통과시킨다 —
 * Slack 장애가 신청 장애가 되면 안 된다.
 */
@Slf4j
@Component
public class SlackMembershipGate {

    /** 방금 가입한 사람이 캐시 때문에 막히지 않게 목록을 다시 받되, Slack 호출 한도를 넘지 않게 간격을 둔다. */
    static final Duration REFRESH_INTERVAL = Duration.ofMinutes(1);

    private final SlackApiService slackApiService;
    private final boolean enabled;
    private final Clock clock;
    private final AtomicLong lastRefreshMillis = new AtomicLong(Long.MIN_VALUE);

    @Autowired
    public SlackMembershipGate(SlackApiService slackApiService,
                               @Value("${slack.membership-check.enabled:false}") boolean enabled) {
        this(slackApiService, enabled, Clock.systemUTC());
    }

    SlackMembershipGate(SlackApiService slackApiService, boolean enabled, Clock clock) {
        this.slackApiService = slackApiService;
        this.enabled = enabled;
        this.clock = clock;
    }

    /** @throws BusinessException 그 이름의 Slack 회원이 없을 때({@link ErrorCode#SLACK_MEMBERSHIP_REQUIRED}) */
    public void requireMember(String name) {
        if (!enabled) {
            return;
        }
        boolean member;
        try {
            member = slackApiService.hasMemberNamed(name) || (refreshIfDue() && slackApiService.hasMemberNamed(name));
        } catch (RuntimeException e) {
            log.warn("Slack 회원 여부를 확인하지 못해 신청을 그대로 받는다: {}", e.toString());
            return;
        }
        if (!member) {
            throw new BusinessException(ErrorCode.SLACK_MEMBERSHIP_REQUIRED);
        }
    }

    private boolean refreshIfDue() {
        long now = clock.millis();
        long last = lastRefreshMillis.get();
        boolean due = last == Long.MIN_VALUE || now - last >= REFRESH_INTERVAL.toMillis();
        if (!due || !lastRefreshMillis.compareAndSet(last, now)) {
            return false;
        }
        slackApiService.refreshSlackUserCache();
        return true;
    }
}
