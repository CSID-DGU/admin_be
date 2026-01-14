package DGU_AI_LAB.admin_be.domain.scheduler;

import DGU_AI_LAB.admin_be.domain.alarm.service.AlarmService;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.requests.service.UbuntuAccountService;
import DGU_AI_LAB.admin_be.domain.usedIds.entity.UsedId;
import DGU_AI_LAB.admin_be.domain.usedIds.service.IdAllocationService;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.global.event.RequestExpiredEvent;
import DGU_AI_LAB.admin_be.global.util.MessageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RequestSchedulerService {

    private final RequestRepository requestRepository;
    private final AlarmService alarmService;
    private final UbuntuAccountService ubuntuAccountService;
    private final IdAllocationService idAllocationService;
    private final ApplicationEventPublisher eventPublisher;
    private final ApplicationContext applicationContext;
    private final MessageUtils messageUtils;

    @Scheduled(cron = "0 46 22 * * ?", zone = "Asia/Seoul")
    public void runScheduler() {
        log.info("🗓️ [스케줄러 시작] 만료 계정 관리 작업");
        LocalDateTime now = LocalDateTime.now();

        sendPreExpiryNotification(now.plusDays(7), "7일");
        sendPreExpiryNotification(now.plusDays(3), "3일");
        sendPreExpiryNotification(now.plusDays(1), "1일");

        processExpiredRequests(now);

        log.info("🗓️ [스케줄러 종료]");
    }

    public void processExpiredRequests(LocalDateTime now) {
        List<Request> expiredRequests = requestRepository.findAllWithUserByExpiredDateBefore(now);
        if (expiredRequests.isEmpty()) return;

        RequestSchedulerService self = applicationContext.getBean(RequestSchedulerService.class);

        for (Request request : expiredRequests) {
            String serverName = "Unknown";
            String username = request.getUbuntuUsername();

            try {
                if (request.getResourceGroup() != null) {
                    serverName = request.getResourceGroup().getServerName();
                }
                self.deleteExpiredRequest(request.getRequestId());

            } catch (Exception e) {
                log.error("계정 삭제 실패 (ID: {}): {}", request.getRequestId(), e.getMessage());
                sendFailureAlertToAdmin(serverName, username, e.getMessage());
            }
        }
    }

    @Transactional
    public void deleteExpiredRequest(Long requestId) {
        Request request = requestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found"));

        if (request.getStatus() != Status.FULFILLED) return;

        String serverName = request.getResourceGroup().getServerName();
        String ubuntuUsername = request.getUbuntuUsername();
        User user = request.getUser();

        ubuntuAccountService.deleteUbuntuAccount(ubuntuUsername);

        UsedId usedId = request.getUbuntuUid();
        if (usedId != null) {
            request.assignUbuntuUid(null);
            idAllocationService.releaseId(usedId);
        }

        request.delete();
        eventPublisher.publishEvent(new RequestExpiredEvent(user, ubuntuUsername, serverName));
        log.info("삭제 트랜잭션 성공: {}", ubuntuUsername);
    }

    @Transactional(readOnly = true)
    public void sendPreExpiryNotification(LocalDateTime targetDate, String dayLabel) {
        LocalDateTime start = targetDate.toLocalDate().atStartOfDay();
        LocalDateTime end = targetDate.toLocalDate().atTime(23, 59, 59);

        List<Request> requests = requestRepository.findAllByExpiresAtBetweenAndStatus(start, end, Status.FULFILLED);

        for (Request request : requests) {
            try {
                User user = request.getUser();
                String serverName = request.getResourceGroup().getServerName();
                String expireDate = request.getExpiresAt().toLocalDate().toString();
                String subject = messageUtils.get("notification.pre-expiry.subject", dayLabel);
                String message = messageUtils.get("notification.pre-expiry.body",
                        user.getName(), dayLabel, expireDate, serverName, request.getUbuntuUsername());

                alarmService.sendAllAlerts(user.getName(), user.getEmail(), subject, message);

            } catch (Exception e) {
                log.warn("{} 전 알림 실패: {}", dayLabel, e.getMessage());
            }
        }
    }

    private void sendFailureAlertToAdmin(String serverName, String username, String errorMsg) {
        try {
            String type = getServerType(serverName);
            String msg = messageUtils.get("notification.admin.delete.fail",
                    type, serverName, username, errorMsg);

            alarmService.sendAdminSlackNotification(serverName, msg);
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