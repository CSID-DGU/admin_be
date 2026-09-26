package DGU_AI_LAB.admin_be.domain.scheduler;

import DGU_AI_LAB.admin_be.domain.requests.job.JobClient;
import DGU_AI_LAB.admin_be.domain.requests.job.JobResults;
import DGU_AI_LAB.admin_be.domain.alarm.service.AlarmService;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.JobResultResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.requests.service.AdminRequestCommandService;
import DGU_AI_LAB.admin_be.domain.requests.service.RequestExpiryService;
import DGU_AI_LAB.admin_be.global.util.MessageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
    private final JobClient jobClient;

    // 이 시간 넘게 상태가 바뀌지 않은 요청을 재조정 대상으로 본다. PROCESSING은 여기에 더해 생성 작업의
    // 상태를 확인하고 되돌리므로(reconcileStaleProcessing), 재시도로 길어진 작업을 시간만 보고 되돌리지 않는다.
    // 실험 스택에서만 짧은 값을 주입하고 운영은 기본값(20)을 그대로 쓴다. @RequiredArgsConstructor가 만드는
    // 생성자에 @Value가 따라가지 않도록 non-final 필드로 둔다(lombok.config의 copyableAnnotations 참고).
    @Value("${scheduler.stale-threshold-minutes:20}")
    private long staleInFlightThresholdMinutes = 20;

    @Scheduled(cron = "${scheduler.expiry-cron:0 00 08 * * ?}", zone = "Asia/Seoul")
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
    @Scheduled(fixedRateString = "${scheduler.reconcile-rate-ms:300000}")
    public void reconcileStaleInFlightRequests() {
        LocalDateTime staleBefore = LocalDateTime.now(ZoneId.of("Asia/Seoul"))
                .minusMinutes(staleInFlightThresholdMinutes);

        for (Request request : requestRepository.findAllByStatusAndUpdatedAtBefore(Status.PROCESSING, staleBefore)) {
            reconcileStaleProcessing(request);
        }
        for (Request request : requestRepository.findAllByStatusAndUpdatedAtBefore(Status.MIGRATING, staleBefore)) {
            alertStaleMigrating(request);
        }
        for (Request request : requestRepository.findAllByStatusAndUpdatedAtBefore(Status.EXPIRING, staleBefore)) {
            reconcileStaleExpiring(request);
        }
    }

    /**
     * PROCESSING은 승인 때 등록한 생성 작업이 도는 동안의 상태다. 작업은 재시도로 길어질 수 있어, 시간만
     * 보고 되돌리면 작업은 계속 도는데 신청만 PENDING이 되고 재승인 때 컨테이너가 두 번 만들어진다.
     * 그래서 작업이 없거나(등록 전에 admin_be가 죽음) 실패로 끝났을 때만 되돌린다. 성공·결과 불명은 작업
     * 결과 폴러가 처리하고, 실행 중이면 다음 바퀴에 다시 본다. 작업 상태를 조회하지 못하면 되돌리지 않는다.
     */
    private void reconcileStaleProcessing(Request request) {
        Long requestId = request.getRequestId();
        String phase;
        try {
            JobResultResponseDTO job = jobClient.getResult(JobResults.KIND_PROVISION, requestId);
            // 자원을 남긴 실패(DEGRADED)는 되돌리면 안 되므로 결과 불명과 같이 취급한다.
            phase = JobResults.isDegraded(job) ? JobResults.ERROR_DEGRADED : job.phase();
        } catch (Exception e) {
            log.warn("🔧 [재조정] 생성 작업 상태를 조회하지 못해 PROCESSING 요청을 그대로 둔다: requestId={}", requestId, e);
            return;
        }
        if (!JobResults.PHASE_NONE.equals(phase) && !JobResults.PHASE_FAIL.equals(phase)) {
            log.info("🔧 [재조정] 생성 작업이 {} 상태라 PROCESSING 요청을 되돌리지 않는다: requestId={}", phase, requestId);
            return;
        }
        log.warn("🔧 [재조정] {}분 넘게 PROCESSING 상태로 방치된 요청을 PENDING으로 복구 시도 (생성 작업 {}): requestId={}",
                staleInFlightThresholdMinutes, phase, requestId);
        // 락 + 상태 재확인은 revertToPendingIfStillProcessing 내부에서 수행 — 그 사이 정상
        // 처리(승인/거절)됐으면 건드리지 않는다.
        adminRequestCommandService.revertToPendingIfStillProcessing(
                request.getRequestId(), request.getResourceGroup().getServerName());
        try {
            String msg = messageUtils.get("notification.admin.request.stale-processing",
                    request.getRequestId(), request.getUbuntuUsername(), staleInFlightThresholdMinutes);
            alarmService.sendSlackAlert(msg, null);
        } catch (Exception ignored) {}
    }

    /**
     * EXPIRING은 회수 작업이 도는 동안의 상태다. 결과는 RevokeJobPoller가 반영하므로, 여기서는 작업이 아예
     * 등록되지 않은 경우(선점 뒤 등록 전에 admin_be가 멈춤)만 FULFILLED로 되돌려 다음 만료 회차에 재시도시킨다.
     * 작업이 돌거나 끝났으면 폴러에 맡기고, 작업 상태를 조회하지 못하면 되돌리지 않는다.
     */
    private void reconcileStaleExpiring(Request request) {
        Long requestId = request.getRequestId();
        String phase;
        try {
            phase = jobClient.getResult(JobResults.KIND_REVOKE, requestId).phase();
        } catch (Exception e) {
            log.warn("🔧 [재조정] 회수 작업 상태를 조회하지 못해 EXPIRING 요청을 그대로 둔다: requestId={}", requestId, e);
            return;
        }
        if (!JobResults.PHASE_NONE.equals(phase)) {
            log.info("🔧 [재조정] 회수 작업이 {} 상태라 EXPIRING 요청을 결과 폴러에 맡긴다: requestId={}", phase, requestId);
            return;
        }
        log.warn("🔧 [재조정] {}분 넘게 회수 작업 없이 EXPIRING 상태로 방치된 요청을 FULFILLED로 복구해 다음 만료 스케줄에서 재시도: requestId={}",
                staleInFlightThresholdMinutes, requestId);
        requestExpiryService.revertStaleExpiring(requestId);
    }

    private void alertStaleMigrating(Request request) {
        log.error("🔧 [재조정] {}분 넘게 MIGRATING 상태로 방치된 요청 발견 — 실제 인프라 상태와 충돌할 수 있어 " +
                        "자동 복구하지 않고 알림만 발송: requestId={}",
                staleInFlightThresholdMinutes, request.getRequestId());
        try {
            String msg = messageUtils.get("notification.admin.request.stale-migrating",
                    request.getRequestId(), request.getUbuntuUsername(), staleInFlightThresholdMinutes);
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
