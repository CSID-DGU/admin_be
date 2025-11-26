package DGU_AI_LAB.admin_be.domain.scheduler; // 새로운 패키지

import DGU_AI_LAB.admin_be.domain.alarm.service.AlarmService;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.requests.service.UbuntuAccountService;
import DGU_AI_LAB.admin_be.domain.usedIds.entity.UsedId;
import DGU_AI_LAB.admin_be.domain.usedIds.service.IdAllocationService;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
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
    // Self-invocation으로 트랜잭션 분리
    private final ApplicationContext applicationContext;

    /**
     * 매일 오전 10시에 실행되는 주 스케줄러 메서드
     */
    //@Scheduled(cron = "0 0 10 * * ?", zone = "Asia/Seoul")
    @Scheduled(cron = "0 45 16 * * ?", zone = "Asia/Seoul")
    public void checkAndProcessExpiredRequests() {
        log.info("만료 계정 확인 스케줄러 시작...");

        RequestSchedulerService self = applicationContext.getBean(RequestSchedulerService.class);
        LocalDateTime now = LocalDateTime.now();

        try {
            // 1. 만료 7일 전 알림 (읽기 전용 트랜잭션)
            self.processPreExpiryNotifications(now.plusDays(7), "7일");

            // 2. 만료 1일 전 알림 (읽기 전용 트랜잭션)
            self.processPreExpiryNotifications(now.plusDays(1), "1일");
        } catch (Exception e) {
            log.error("만료 전 알림 처리 중 오류 발생", e);
        }

        // 3. 만료된 계정 목록 조회
        List<Request> expiredRequests;
        try {
            expiredRequests = requestRepository.findAllByExpiresAtBeforeAndStatus(now, Status.FULFILLED);
        } catch (Exception e) {
            log.error("만료 계정 조회 중 DB 오류. 스케줄러를 종료합니다.", e);
            return;
        }

        log.info("만료되어 삭제할 계정 {}건 발견.", expiredRequests.size());

        // 4. 만료된 계정 개별 삭제 처리 (개별 트랜잭션)
        for (Request request : expiredRequests) {
            // ⭐️ 중요: 에러 발생 시(catch 블록) 사용하기 위해 필요한 정보를 미리 문자열로 추출
            // 세션이 닫혀도 문제 없도록 미리 get 해둡니다.
            Long requestId = request.getRequestId();
            String username = request.getUbuntuUsername();
            String userEmail = request.getUser().getEmail();
            String userName = request.getUser().getName();
            String serverName = "Unknown Server";
            String expireDate = request.getExpiresAt().toLocalDate().toString();

            try {
                // ResourceGroup 접근 시 Lazy Loading 에러 방지용 try-catch
                serverName = request.getResourceGroup().getServerName();
            } catch (Exception ignored) {
                log.warn("서버 이름 조회 실패 (Lazy Loading Issue Possibility)");
            }

            try {
                // 1) 트랜잭션 작업: DB 및 외부 서버 계정 삭제만 수행 (알림 X)
                self.deleteAccountTransaction(requestId);

                // 2) 트랜잭션 커밋 후: 성공 알림 발송 (여기서 에러 나도 DB 롤백 안 됨)
                sendSuccessNotification(userName, userEmail, username, serverName, expireDate);

            } catch (Exception e) {
                // 3) 실패 처리: 트랜잭션은 이미 롤백됨. 관리자 알림 발송
                log.error("만료 계정 삭제 처리 실패. Request ID: {}. 원인: {}", requestId, e.getMessage(), e);
                sendFailureNotification(username, requestId, serverName, e.getMessage());
            }
        }
        log.info("만료 계정 확인 스케줄러 종료.");
    }

    /**
     * 알림 로직을 여기서 제거하여, 알림 실패가 DB 롤백을 유발하지 않도록 합니다.
     */
    @Transactional
    public void deleteAccountTransaction(Long requestId) {
        Request request = requestRepository.findById(requestId)
                .orElseThrow(() -> new BusinessException("Request not found", ErrorCode.RESOURCE_NOT_FOUND));

        if (request.getStatus() != Status.FULFILLED) {
            return; // 이미 처리됨
        }

        // 1. 외부 서버 계정 삭제
        ubuntuAccountService.deleteUbuntuAccount(request.getUbuntuUsername());

        // 2. UsedId 반환
        UsedId usedId = request.getUbuntuUid();
        if (usedId != null) {
            request.assignUbuntuUid(null);
            idAllocationService.releaseId(usedId);
        }

        // 3. 상태 변경 (Soft Delete)
        request.delete();

        // 트랜잭션 종료 -> Commit 발생
    }

    /**
     * 성공 알림 (트랜잭션 밖에서 실행)
     */

    private void sendSuccessNotification(String name, String email, String username, String serverName, String expireDate) {
        try {
            String subject = "[DGU AI LAB] 서버 사용 기간 만료 및 계정 삭제 안내";
            String message = String.format(
                    """
                    %s님의 서버 사용 기간(%s)이 만료되어 계정이 삭제되었습니다.
                    ... (생략) ...
                    """, name, expireDate, username, serverName);

            alarmService.sendAllAlerts(name, email, subject, message);

            String adminMessage = String.format("✅ 계정 삭제 완료: %s (%s)", username, serverName);
            alarmService.sendAdminSlackNotification(serverName, adminMessage);

        } catch (Exception e) {
            log.error("삭제 성공했으나 알림 전송 실패: {}", username, e);
            // 알림 실패해도 DB 삭제는 유지됨!
        }
    }

    /**
     * 실패 알림 (트랜잭션 밖에서 실행, 변수로 받아온 값을 사용해 Lazy Loading 에러 방지)
     */
    private void sendFailureNotification(String username, Long requestId, String serverName, String errorMsg) {
        try {
            alarmService.sendAdminSlackNotification(
                    serverName,
                    String.format("❌ 계정 삭제 실패: %s (ID: %d)\n오류: %s", username, requestId, errorMsg)
            );
        } catch (Exception slackEx) {
            log.error("삭제 실패 알림 전송 실패", slackEx);
        }
    }

    /**
     * 만료 전 알림을 처리합니다. (읽기 전용 트랜잭션)
     */
    @Transactional(readOnly = true)
    public void processPreExpiryNotifications(LocalDateTime targetExpiryDate, String daysRemaining) {
        LocalDateTime startOfDay = targetExpiryDate.toLocalDate().atStartOfDay();
        LocalDateTime endOfDay = targetExpiryDate.toLocalDate().atTime(23, 59, 59);

        List<Request> requests = requestRepository.findAllByExpiresAtBetweenAndStatus(startOfDay, endOfDay, Status.FULFILLED);

        if (!requests.isEmpty()) {
            log.info("[{}] 후 만료 예정인 계정 {}건 발견.", daysRemaining, requests.size());
        }

        for (Request request : requests) {
            try {
                User user = request.getUser();
                String subject = String.format("[DGU AI LAB] 서버 사용 만료 %s 전 안내", daysRemaining);
                String message = String.format(
                        """
                        %s님의 서버 사용 기간이 %s 후 (%s) 만료될 예정입니다.
                        
                        - Ubuntu 사용자 이름: %s
                        - 할당된 서버: %s
                        
                        기간 연장이 필요하신 경우, 관리자 페이지에서 연장 신청을 해 주시기 바랍니다.
                        별도 조치가 없을 시 계정은 자동 삭제됩니다.
                        """,
                        user.getName(),
                        daysRemaining,
                        request.getExpiresAt().toLocalDate().toString(),
                        request.getUbuntuUsername(),
                        request.getResourceGroup().getServerName()
                );

                // 1. 사용자에게 이메일 + 슬랙 DM
                alarmService.sendAllAlerts(user.getName(), user.getEmail(), subject, message);

                // 2. 관리자에게 슬랙 알림
                String adminMessage = String.format(
                        "🔔 계정 만료 %s 전 알림 🔔\n" +
                                "▶ 사용자: %s (%s)\n" +
                                "▶ 계정: %s\n" +
                                "▶ 서버: %s\n" +
                "▶ 만료일: %s",
                        daysRemaining,
                        user.getName(), user.getEmail(),
                        request.getUbuntuUsername(),
                        request.getResourceGroup().getServerName(),
                        request.getExpiresAt().toLocalDate().toString()
                );
                alarmService.sendAdminSlackNotification(request.getResourceGroup().getServerName(), adminMessage);

            } catch (Exception e) {
                log.error("만료 {}일 전 알림 전송 실패. Request ID: {}", daysRemaining, request.getRequestId(), e);
            }
        }
    }

    /**
     * 만료된 개별 Request를 트랜잭션 단위로 처리합니다.
     */
    @Transactional
    public void processSingleExpiredRequest(Long requestId) {
        Request request = requestRepository.findById(requestId)
                .orElseThrow(() -> new BusinessException("Request not found: " + requestId, ErrorCode.RESOURCE_NOT_FOUND));

        if (request.getStatus() != Status.FULFILLED) {
            log.warn("이미 처리되었거나 FULFILLED 상태가 아닌 Request. ID: {}, Status: {}", requestId, request.getStatus());
            return;
        }

        User user = request.getUser();
        String username = request.getUbuntuUsername();
        UsedId usedId = request.getUbuntuUid();
        String serverName = request.getResourceGroup().getServerName();

        // --- 트랜잭션 시작 ---
        // 1. 실제 우분투 계정 및 PVC 삭제 요청 (외부 서버)
        // 이 메서드가 실패하면 BusinessException을 발생시켜 트랜잭션이 롤백됨.
        ubuntuAccountService.deleteUbuntuAccount(username);
        log.info("외부 서버 계정/PVC 삭제 성공: {}", username);

        // 2. UsedId 반환 (DB에서 UsedId 삭제)
        if (usedId != null) {
            request.assignUbuntuUid(null); // 연관관계 제거 (Dirty checking)
            idAllocationService.releaseId(usedId);
            log.info("UID 반환 성공: {}", usedId.getIdValue());
        }

        // 3. Request 상태 DELETED로 변경 (Soft delete)
        request.delete();
        log.info("Request 상태 DELETED로 변경: {}", username);

        // --- 트랜잭션 커밋 ---
        // 4. 삭제 완료 알림 (트랜잭션이 성공적으로 커밋된 후에 실행)
        try {
            String subject = "[DGU AI LAB] 서버 사용 기간 만료 및 계정 삭제 안내";
            String message = String.format(
                    """
                    %s님의 서버 사용 기간(%s)이 만료되어 계정이 삭제되었습니다.
                    
                    - Ubuntu 사용자 이름: %s
                    - 할당된 서버: %s
                    
                    데이터는 모두 삭제되었으며, 복구가 불가능합니다.
                    서버 재사용이 필요하신 경우, 신규 신청을 해 주시기 바랍니다.
                    """,
                    user.getName(),
                    request.getExpiresAt().toLocalDate().toString(),
                    username,
                    serverName
            );
            alarmService.sendAllAlerts(user.getName(), user.getEmail(), subject, message);

            // 관리자 알림
            String adminMessage = String.format(
                    "✅ 계정 삭제 완료 ✅\n" +
                            "▶ 사용자: %s (%s)\n" +
                            "▶ 계정: %s\n" +
                            "▶ 서버: %s\n" +
            "▶ 만료일: %s",
                    user.getName(), user.getEmail(),
                    username,
                    serverName,
                    request.getExpiresAt().toLocalDate().toString()
            );
            alarmService.sendAdminSlackNotification(serverName, adminMessage);

            log.info("계정 삭제 및 알림 처리 완료: {}", username);

        } catch (Exception e) {
            log.error("삭제 완료 알림 전송 실패. Request ID: {}", requestId, e);
        }
    }
}