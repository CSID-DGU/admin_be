package DGU_AI_LAB.admin_be.domain.home.service;

import DGU_AI_LAB.admin_be.domain.alarm.SlackText;
import DGU_AI_LAB.admin_be.domain.alarm.service.AlarmService;
import DGU_AI_LAB.admin_be.global.util.MessageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 홈 삭제 결과를 관리자에게 알린다 — 성공은 알림 기록 채널, 실패는 오류 채널. 사용자에게는 보내지 않는다(삭제일은
 * 컨테이너가 끝나기 전에 이미 알렸다). 알림은 부가 동작이라 실패해도 예외를 밖으로 내지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HomeCleanupNotifier {

    private final AlarmService alarmService;
    private final MessageUtils messageUtils;

    public void deleted(HomeCleanupService.Target target) {
        try {
            alarmService.sendNotiLog(messageUtils.get("notification.admin.home-cleanup.success",
                    SlackText.escape(target.ubuntuUsername()), target.lastContainerEndedAt().toLocalDate().toString()));
        } catch (RuntimeException e) {
            log.warn("[homeCleanup] 삭제 완료 알림 실패: cleanupId={}", target.cleanupId(), e);
        }
    }

    public void failed(HomeCleanupService.Target target, String failureCode) {
        try {
            // url이 null이면 오류 채널로 간다.
            alarmService.sendSlackAlert(messageUtils.get("notification.admin.home-cleanup.fail",
                    SlackText.escape(target.ubuntuUsername()), String.valueOf(target.cleanupId()),
                    SlackText.escape(failureCode)), null);
        } catch (RuntimeException e) {
            log.warn("[homeCleanup] 삭제 실패 알림 실패: cleanupId={}", target.cleanupId(), e);
        }
    }
}
