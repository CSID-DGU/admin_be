package DGU_AI_LAB.admin_be.domain.scheduler;

import DGU_AI_LAB.admin_be.domain.alarm.service.SlackApiService;
import DGU_AI_LAB.admin_be.global.event.RequestExpiredEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class RequestEventListener {

    private final SlackApiService slackApiService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    // AFTER_COMMIT 옵션 때문에 DB 저장이 완벽히 끝난 후에만 실행됨
    public void handleExpiredEvent(RequestExpiredEvent event) {
        try {
            slackApiService.sendDM(event.user());
        } catch (Exception e) {
            // 알림 실패가 DB 롤백을 유발하면 안 되므로 로그만 제공
            log.warn("사용자(ID:{})에게 만료 알림 전송 실패: {}", event.user().getUserId(), e.getMessage());
        }
    }
}