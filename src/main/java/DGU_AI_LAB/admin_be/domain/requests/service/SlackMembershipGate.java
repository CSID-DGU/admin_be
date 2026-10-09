package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.domain.alarm.service.SlackApiService;
import DGU_AI_LAB.admin_be.domain.alarm.service.SlackApiService.Membership;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
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
 * 가입하지 않은 사람은 안내를 받지 못한다. 회원 정보의 이름이 Slack 이름과 같고, Slack에 가입한 이메일이 학교 이메일이나
 * 자주 사용하는 이메일과 같아야 같은 사람으로 본다 — 이름만 보면 동명이인을 가리지 못한다.
 *
 * <p>{@code slack.membership-check.enabled}를 켠 환경에서만 막는다(기본값 false). Slack을 조회하지 못하면 통과시킨다 —
 * Slack 장애가 신청 장애가 되면 안 된다. 봇에 이메일 조회 권한이 없어 Slack이 이메일을 주지 않으면 이름만 본다.
 */
@Slf4j
@Component
public class SlackMembershipGate {

    /** 방금 가입한 사람이 캐시 때문에 막히지 않게 목록을 다시 받되, Slack 호출 한도를 넘지 않게 간격을 둔다. */
    static final Duration REFRESH_INTERVAL = Duration.ofMinutes(1);

    private final SlackApiService slackApiService;
    private final SlackMembershipRefusalNotice refusalNotice;
    private final boolean enabled;
    private final Clock clock;
    private final AtomicLong lastRefreshMillis = new AtomicLong(Long.MIN_VALUE);

    @Autowired
    public SlackMembershipGate(SlackApiService slackApiService, SlackMembershipRefusalNotice refusalNotice,
                               @Value("${slack.membership-check.enabled:false}") boolean enabled) {
        this(slackApiService, refusalNotice, enabled, Clock.systemUTC());
    }

    SlackMembershipGate(SlackApiService slackApiService, SlackMembershipRefusalNotice refusalNotice,
                        boolean enabled, Clock clock) {
        this.slackApiService = slackApiService;
        this.refusalNotice = refusalNotice;
        this.enabled = enabled;
        this.clock = clock;
    }

    /** {@link #check}의 답. */
    public enum Verdict {
        MEMBER,
        NOT_MEMBER,
        /** 확인을 꺼 둔 환경이거나 Slack을 조회하지 못했다. 신청은 막지 않는다. */
        UNCHECKED
    }

    /**
     * 거절하면 무엇을 고쳐야 하는지 메일로도 알린다.
     *
     * @throws BusinessException 그 이름·이메일의 Slack 회원이 없을 때({@link ErrorCode#SLACK_MEMBERSHIP_REQUIRED})
     */
    public void requireMember(User user) {
        if (check(user) == Verdict.NOT_MEMBER) {
            refusalNotice.send(user);
            throw new BusinessException(ErrorCode.SLACK_MEMBERSHIP_REQUIRED);
        }
    }

    /** 막지 않고 답만 한다 — 신청하기 전에 화면이 미리 알려 줄 때 쓴다. */
    public Verdict check(User user) {
        if (!enabled) {
            return Verdict.UNCHECKED;
        }
        Membership membership;
        try {
            membership = find(user);
            if (membership == Membership.NOT_FOUND && refreshIfDue()) {
                membership = find(user);
            }
        } catch (RuntimeException e) {
            log.warn("Slack 회원 여부를 확인하지 못했다: {}", e.toString());
            return Verdict.UNCHECKED;
        }
        if (membership == Membership.NAME_ONLY) {
            log.warn("Slack이 회원 이메일을 주지 않아 이름만 확인했다 — 봇에 users:read.email 권한이 있는지 확인 필요");
        }
        return membership == Membership.NOT_FOUND ? Verdict.NOT_MEMBER : Verdict.MEMBER;
    }

    private Membership find(User user) {
        return slackApiService.findMembership(user.getName(), user.knownEmails());
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
