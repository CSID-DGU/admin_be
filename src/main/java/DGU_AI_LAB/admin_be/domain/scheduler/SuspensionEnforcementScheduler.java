package DGU_AI_LAB.admin_be.domain.scheduler;

import DGU_AI_LAB.admin_be.domain.warnings.service.AccessEnforcementService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 이용 정지 여부와 실제 접속 상태를 주기적으로 맞춘다. 정지가 끝난 사용자의 접속을 여는 일과, 실패한 차단·해제를
 * 다시 시도하는 일이 여기서 일어난다. 정지는 일 단위라 자주 볼 필요가 없다 — 기한이 지난 뒤 길게는 한 주기만큼
 * 늦게 열린다. 정지 시작과 경고 취소는 이 주기를 기다리지 않고 그 자리에서 맞춘다(WarningService).
 */
@Component
@RequiredArgsConstructor
public class SuspensionEnforcementScheduler {

    private final AccessEnforcementService accessEnforcementService;

    @Scheduled(fixedDelayString = "${warnings.enforcement.poll-ms:3600000}",
            initialDelayString = "${warnings.enforcement.initial-delay-ms:60000}")
    public void enforceSuspensions() {
        accessEnforcementService.enforceAll();
    }
}
