package DGU_AI_LAB.admin_be.domain.scheduler;

import DGU_AI_LAB.admin_be.domain.alarm.service.AlarmService;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.requests.service.AdminRequestCommandService;
import DGU_AI_LAB.admin_be.domain.requests.service.RequestExpiryService;
import DGU_AI_LAB.admin_be.global.util.MessageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Business Trigger
 * 사용자의 상태를 주기적으로 검사해서 알림을 Trigger하는 Main Business Logic입니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RequestSchedulerService {

    private final RequestRepository requestRepository;
    private final AlarmService alarmService;
    private final RequestExpiryService requestExpiryService;
    private final MessageUtils messageUtils;
    private final RequestNotificationService requestNotificationService;
    private final AdminRequestCommandService adminRequestCommandService;

    // 승인 처리 API가 보통 몇 초~1분 내로 끝나는 걸 감안해, 이보다 훨씬 길게 잡아 정상
    // 처리 중인 요청을 잘못 회수하지 않게 한다.
    private static final long STALE_IN_FLIGHT_THRESHOLD_MINUTES = 10;

    @Scheduled(cron = "0 00 08 * * ?", zone = "Asia/Seoul")
    public void runScheduler() {
        log.info("🗓️ [스케줄러 시작] 만료 계정 관리 작업");
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));

        requestNotificationService.sendPreExpiryNotification(now.plusDays(7), "7일");
        requestNotificationService.sendPreExpiryNotification(now.plusDays(3), "3일");
        requestNotificationService.sendPreExpiryNotification(now.plusDays(1), "1일");

        processExpiredRequests(now);

        log.info("🗓️ [스케줄러 종료]");
    }

    /**
     * 정지된(stale) PROCESSING/MIGRATING 요청 재조정(reconciliation). approveRequest/
     * PodMigrationService의 보상 트랜잭션은 전부 try/catch 안에서만 실행되므로, admin_be
     * 프로세스 자체가 처리 도중 죽으면(강제 재배포, OOM 등) catch가 실행될 기회조차 없이
     * 그 요청은 PROCESSING/MIGRATING에 영구히 갇힌다. 5분마다 돌면서 임계치를 넘겨 방치된
     * 요청을 찾아, PROCESSING은 안전하게 PENDING으로 되돌리고(재승인/재거절 가능하게),
     * MIGRATING은 실제 Pod 생성/삭제가 걸려있어 자동 복구 대신 관리자 알림만 보낸다.
     */
    @Scheduled(fixedRate = 5 * 60 * 1000)
    public void reconcileStaleInFlightRequests() {
        LocalDateTime staleBefore = LocalDateTime.now(ZoneId.of("Asia/Seoul"))
                .minusMinutes(STALE_IN_FLIGHT_THRESHOLD_MINUTES);

        for (Request request : requestRepository.findAllByStatusAndUpdatedAtBefore(Status.PROCESSING, staleBefore)) {
            reconcileStaleProcessing(request);
        }
        for (Request request : requestRepository.findAllByStatusAndUpdatedAtBefore(Status.MIGRATING, staleBefore)) {
            alertStaleMigrating(request);
        }
    }

    private void reconcileStaleProcessing(Request request) {
        log.warn("🔧 [재조정] {}분 넘게 PROCESSING 상태로 방치된 요청을 PENDING으로 복구 시도: requestId={}",
                STALE_IN_FLIGHT_THRESHOLD_MINUTES, request.getRequestId());
        // 락 + 상태 재확인은 revertToPendingIfStillProcessing 내부에서 수행 — 그 사이 정상
        // 처리(승인/거절)됐으면 건드리지 않는다.
        adminRequestCommandService.revertToPendingIfStillProcessing(request.getRequestId());
        try {
            String msg = messageUtils.get("notification.admin.request.stale-processing",
                    request.getRequestId(), request.getUbuntuUsername(), STALE_IN_FLIGHT_THRESHOLD_MINUTES);
            alarmService.sendSlackAlert(msg, null);
        } catch (Exception ignored) {}
    }

    private void alertStaleMigrating(Request request) {
        log.error("🔧 [재조정] {}분 넘게 MIGRATING 상태로 방치된 요청 발견 — 실제 인프라 상태와 충돌할 수 있어 " +
                        "자동 복구하지 않고 알림만 발송: requestId={}",
                STALE_IN_FLIGHT_THRESHOLD_MINUTES, request.getRequestId());
        try {
            String msg = messageUtils.get("notification.admin.request.stale-migrating",
                    request.getRequestId(), request.getUbuntuUsername(), STALE_IN_FLIGHT_THRESHOLD_MINUTES);
            alarmService.sendSlackAlert(msg, null);
        } catch (Exception ignored) {}
    }

    public void processExpiredRequests(LocalDateTime now) {
        List<Request> expiredRequests = requestRepository.findAllWithUserByExpiredDateBefore(now, Status.FULFILLED);
        if (expiredRequests.isEmpty()) return;

        for (Request request : expiredRequests) {
            String serverName = "Unknown";
            String username = request.getUbuntuUsername();

            try {
                if (request.getResourceGroup() != null) {
                    serverName = request.getResourceGroup().getServerName();
                }
                requestExpiryService.deleteExpiredRequest(request.getRequestId());

            } catch (Exception e) {
                log.error("계정 삭제 실패 (ID: {}): {}", request.getRequestId(), e.getMessage());
                sendFailureAlertToAdmin(serverName, username, e.getMessage());
            }
        }
    }

    /**
     * 1. 비즈니스 관리자 (Farm/Lab) 채널 알림
     * 2. 시스템 에러 (Error Log) 채널 알림
     */
    private void sendFailureAlertToAdmin(String serverName, String username, String errorMsg) {
        try {
            String type = getServerType(serverName);
            String msg = messageUtils.get("notification.admin.delete.fail",
                    type, serverName, username, errorMsg);
            alarmService.sendAdminSlackNotification(serverName, msg);
            // AlarmService.sendSlackAlert에서 url이 null이면 기본값(error-log)으로 전송합니다.
            alarmService.sendSlackAlert(msg, null);

        } catch (Exception ignored) {}
    }

    private String getServerType(String serverName) {
        if (serverName == null) return "UNKNOWN";
        String lower = serverName.toLowerCase();
        if (lower.contains("farm")) return "FARM";
        if (lower.contains("lab") || lower.contains("dgx")) return "LAB";
        return "SERVER";
    }
}
