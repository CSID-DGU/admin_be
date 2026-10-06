package DGU_AI_LAB.admin_be.domain.home.service;

import DGU_AI_LAB.admin_be.domain.alarm.service.AlarmService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 홈 삭제 결과를 관리자에게 알린다 — 성공은 알림 기록 채널, 실패는 오류 채널. 사용자에게는 보내지 않는다(삭제일은
 * 컨테이너가 끝나기 전에 이미 알렸다).
 */
@Component
@RequiredArgsConstructor
public class HomeCleanupNotifier {

    private final AlarmService alarmService;

    public void deleted(HomeCleanupService.Target target) {
        alarmService.recordLog("notification.admin.home-cleanup.success",
                target.ubuntuUsername(), target.lastContainerEndedAt().toLocalDate().toString());
    }

    public void failed(HomeCleanupService.Target target, String failureCode) {
        alarmService.alertNeedsAction("notification.admin.home-cleanup.fail",
                target.ubuntuUsername(), target.cleanupId(), failureCode);
    }
}
