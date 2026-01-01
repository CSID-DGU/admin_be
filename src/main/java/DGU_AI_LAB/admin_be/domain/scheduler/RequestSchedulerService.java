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

    @Scheduled(cron = "0 46 22 * * ?", zone = "Asia/Seoul")
    public void runScheduler() {
        log.info("🗓️ [스케줄러 시작] 만료 계정 관리 작업");
        LocalDateTime now = LocalDateTime.now();

        // 1. 만료 예고 (삭제 예정 알림)
        sendPreExpiryNotification(now.plusDays(7), "7일");
        sendPreExpiryNotification(now.plusDays(3), "3일");
        sendPreExpiryNotification(now.plusDays(1), "1일");

        // 2. 만료 처리 (삭제 및 결과 알림)
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
                // 에러 발생 시 알림을 위해 미리 정보 추출
                if (request.getResourceGroup() != null) {
                    serverName = request.getResourceGroup().getServerName();
                }

                // 트랜잭션 메서드 호출
                self.deleteExpiredRequest(request.getRequestId());

            } catch (Exception e) {
                log.error("계정 삭제 실패 (ID: {}): {}", request.getRequestId(), e.getMessage());

                // [요구사항 3] 리소스 삭제 실패 시 관리자 채널에만 알림
                sendFailureAlertToAdmin(serverName, username, e.getMessage());
            }
        }
    }

    @Transactional
    public void deleteExpiredRequest(Long requestId) {
        Request request = requestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found"));

        if (request.getStatus() != Status.FULFILLED) return;

        // 이벤트 발행을 위해 정보 미리 저장
        String serverName = request.getResourceGroup().getServerName();
        String ubuntuUsername = request.getUbuntuUsername();
        User user = request.getUser();

        // 1. 외부 계정 삭제
        ubuntuAccountService.deleteUbuntuAccount(ubuntuUsername);

        // 2. UID 반환
        UsedId usedId = request.getUbuntuUid();
        if (usedId != null) {
            request.assignUbuntuUid(null);
            idAllocationService.releaseId(usedId);
        }

        // 3. DB Soft Delete
        request.delete();

        // 4. 이벤트 발행 (트랜잭션 커밋 후 리스너 실행)
        // 성공 시 알림은 리스너에게 위임
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

                // 삭제 예정임을 명시
                String subject = String.format("[DGU AI LAB] 서버 계정 삭제 예정 안내 (%s 전)", dayLabel);
                String message = String.format(
                        """
                        안녕하세요, %s님.
                        
                        사용 중인 GPU 서버 계정이 %s 후 (%s)에 만료되어 삭제될 예정입니다.
                        
                        - 서버: %s
                        - 계정: %s
                        
                        삭제된 데이터는 복구할 수 없으니, 중요한 데이터는 미리 백업해 주시기 바랍니다.
                        연장이 필요하시면 관리자에게 문의하세요.
                        """,
                        user.getName(), dayLabel, expireDate, serverName, request.getUbuntuUsername()
                );

                // 사용자에게만 전송 (이메일 + DM)
                alarmService.sendAllAlerts(user.getName(), user.getEmail(), subject, message);

            } catch (Exception e) {
                log.warn("{} 전 알림 실패: {}", dayLabel, e.getMessage());
            }
        }
    }

    private void sendFailureAlertToAdmin(String serverName, String username, String errorMsg) {
        try {
            // 관리자에게 Lab/Farm 구분하여 실패 알림
            String type = getServerType(serverName);
            String msg = String.format("🚨 [%s] 리소스 삭제 실패!\n- 서버: %s\n- 계정: %s\n- 원인: %s",
                    type, serverName, username, errorMsg);

            alarmService.sendAdminSlackNotification(serverName, msg);
        } catch (Exception ignored) {}
    }

    // Lab/Farm 구분 헬퍼 메서드
    private String getServerType(String serverName) {
        if (serverName == null) return "UNKNOWN";
        String lower = serverName.toLowerCase();
        if (lower.contains("farm")) return "FARM";
        if (lower.contains("lab") || lower.contains("dgx")) return "LAB";
        return "SERVER";
    }
}