package DGU_AI_LAB.admin_be.domain.scheduler;

import DGU_AI_LAB.admin_be.domain.alarm.service.AlarmService;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.global.event.RequestExpiredEvent;
import DGU_AI_LAB.admin_be.global.util.MessageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class RequestEventListener {

    private final AlarmService alarmService;
    private final MessageUtils messageUtils;

    // DB 커밋이 완료된 후에만 실행됨
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleExpiredEvent(RequestExpiredEvent event) {
        User user = event.user();
        String serverName = event.serverName();
        String username = event.ubuntuUsername();

        // 1. 사용자 삭제 알림
        try {
            String subject = messageUtils.get("notification.expired.detail.subject");
            String message = messageUtils.get("notification.expired.detail.body",
                    user.getName(), serverName, username);

            alarmService.sendAllAlerts(user.getName(), user.getEmail(), subject, message);
        } catch (Exception e) {
            log.warn("사용자 삭제 알림 전송 실패: {}", e.getMessage());
        }

        // 2. 관리자 알림
        try {
            String type = getServerType(serverName);

            // properties: notification.admin.delete.success ({0}타입, {1}계정, {2}서버)
            String adminMsg = messageUtils.get("notification.admin.delete.success",
                    type, username, serverName);

            alarmService.sendAdminSlackNotification(serverName, adminMsg);
        } catch (Exception e) {
            log.warn("관리자 알림 전송 실패: {}", e.getMessage());
        }
    }

    private String getServerType(String serverName) {
        if (serverName == null) return "ETC";
        String lower = serverName.toLowerCase();
        if (lower.contains("farm")) return "FARM";
        if (lower.contains("lab") || lower.contains("dgx")) return "LAB";
        return "SERVER";
    }
}