package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.domain.alarm.service.AlarmService;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.global.util.AfterTransaction;
import DGU_AI_LAB.admin_be.global.util.RedisWindowCounter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Slack 회원이 아니라서 신청을 받지 않았다는 안내 메일. 화면에도 같은 이유가 뜨지만, 무엇을 고쳐야 하는지 나중에
 * 다시 볼 수 있게 메일로도 남긴다. 고치기 전에 여러 번 눌러도 메일이 쌓이지 않게 간격을 둔다.
 */
@Component
@RequiredArgsConstructor
public class SlackMembershipRefusalNotice {

    static final Duration INTERVAL = Duration.ofMinutes(10);
    private static final String COUNT_PREFIX = "request:slack-refusal-mail:";

    private final RedisWindowCounter windowCounter;
    private final AlarmService alarmService;

    /** 신청 트랜잭션이 끝난 뒤에 보낸다 — 거절이라 롤백되므로 커밋을 기다리면 보내지지 않는다. */
    public void send(User user) {
        Long userId = user.getUserId();
        String name = user.getName();
        String email = user.getEmail();
        AfterTransaction.run("Slack 가입 안내 메일", () -> {
            if (windowCounter.increment(COUNT_PREFIX + userId, INTERVAL) == 1) {
                alarmService.sendSlackMembershipRequiredEmail(name, email);
            }
        });
    }
}
