package DGU_AI_LAB.admin_be.domain.scheduler;

import DGU_AI_LAB.admin_be.domain.alarm.service.AlarmService;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
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

    private final AlarmService alarmService;

    // DB 커밋이 완료된 후에만 실행됨
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleExpiredEvent(RequestExpiredEvent event) {
        User user = event.user();
        String serverName = event.serverName();
        String username = event.ubuntuUsername();

        // 사용자 삭제 알림
        try {
            String subject = "[DGU AI LAB] 서버 계정 삭제 완료 안내";
            String message = String.format(
                    """
                    안녕하세요, %s님.
                    
                    기간 만료로 인해 아래 서버 리소스가 삭제되었습니다.
                    
                    - 서버: %s
                    - 계정: %s
                    
                    이용해 주셔서 감사합니다.
                    """,
                    user.getName(), serverName, username
            );
            // 이메일 + 개인 DM 전송
            alarmService.sendAllAlerts(user.getName(), user.getEmail(), subject, message);
        } catch (Exception e) {
            log.warn("사용자 삭제 알림 전송 실패: {}", e.getMessage());
        }

        // 2. [요구사항 1] 관리자 알림: Lab/Farm 구분하여 간단히 보고
        try {
            String type = getServerType(serverName);
            // 간단 명료한 메시지
            String adminMsg = String.format("🗑️ [%s] 리소스 삭제 완료: %s (%s)", type, username, serverName);

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