package DGU_AI_LAB.admin_be.domain.scheduler;

import DGU_AI_LAB.admin_be.domain.alarm.service.AlarmService;
import DGU_AI_LAB.admin_be.global.event.RequestContainerDeletedEvent;
import DGU_AI_LAB.admin_be.global.event.RequestExpiredEvent;
import DGU_AI_LAB.admin_be.global.util.MessageUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Event Listner
 * 특정 이벤트가 발생했을 때, 이를 감지하고 후속 작업을 처리하는 Observer입니다.
 */
@Component
@RequiredArgsConstructor
public class RequestEventListener {

    private final AlarmService alarmService;
    private final MessageUtils messageUtils;

    // DB 커밋이 완료된 후에만 실행됨
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleExpiredEvent(RequestExpiredEvent event) {
        String userName = event.userName();
        String userEmail = event.userEmail();
        String serverName = event.serverName();
        String username = event.ubuntuUsername();

        // 처리 기록을 먼저 남긴다 — 사용자 안내 문구를 만들다 실패해도 정리했다는 기록은 남는다.
        recordCleanup(serverName, username);

        String subject = messageUtils.get("notification.expired.detail.subject");
        String message = messageUtils.get("notification.expired.detail.body",
                userName, serverName, username,
                event.podName(), event.portSummary(), event.expiresAt(), event.homeNotice());
        alarmService.notifyUser(userName, userEmail, subject, message);
    }

    /**
     * 관리자가 컨테이너 하나를 회수한 경우. 사용자 안내 문구만 만료와 다르고(만료일이 없다),
     * 관리자 쪽 처리 기록은 "리소스 삭제 완료"라 만료와 같은 것을 그대로 쓴다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleContainerDeletedEvent(RequestContainerDeletedEvent event) {
        String userName = event.userName();
        String serverName = event.serverName();
        String username = event.ubuntuUsername();

        recordCleanup(serverName, username);

        String subject = messageUtils.get("notification.deleted.detail.subject");
        String message = messageUtils.get("notification.deleted.detail.body",
                userName, serverName, username, event.podName(), event.portSummary(), event.homeNotice());
        alarmService.notifyUser(userName, event.userEmail(), subject, message);
    }

    // notification.admin.delete.success ({0}서버 표시, {1}계정, {2}서버) — {0}은 DB에 고쳐 둔 템플릿이 그대로 쓰이도록 자리를 유지한다
    private void recordCleanup(String serverName, String username) {
        alarmService.recordLog("notification.admin.delete.success", serverName, username, serverName);
    }

}