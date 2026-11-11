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
                target.ubuntuUsername(), target.lastContainerEndedAt().toLocalDate().toString(),
                freed(target.sizeBytes()));
    }

    /** 지운 홈 크기를 GB 한 자리로. 모르면(못 쟀거나 이미 없던 홈) 그렇게 밝힌다. */
    static String freed(Long sizeBytes) {
        if (sizeBytes == null) {
            return "확보 용량 미확인";
        }
        return String.format(java.util.Locale.ROOT, "%.1f GB 확보", sizeBytes / (1024.0 * 1024 * 1024));
    }

    public void failed(HomeCleanupService.Target target, String failureCode) {
        alarmService.alertNeedsAction("notification.admin.home-cleanup.fail",
                target.ubuntuUsername(), target.cleanupId(), failureCode);
    }
}
