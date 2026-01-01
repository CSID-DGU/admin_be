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
    private final ApplicationContext applicationContext; // Self-Invocation 문제 해결용

    /**
     * 메인 스케줄러 cron = "초 분 시 * * ?"
     */
    @Scheduled(cron = "0 36 22 * * ?", zone = "Asia/Seoul")
    public void runScheduler() {
        log.info("🗓️ [스케줄러 시작] 만료 계정 관리 작업을 시작합니다...");
        LocalDateTime now = LocalDateTime.now();

        // 1. 만료 임박 알림 (7, 3, 1일 전) - 읽기 전용 트랜잭션 사용
        sendPreExpiryNotification(now.plusDays(7), "7일");
        sendPreExpiryNotification(now.plusDays(3), "3일");
        sendPreExpiryNotification(now.plusDays(1), "1일");

        // 2. 만료된 계정 삭제 처리
        processExpiredRequests(now);

        log.info("🗓️ [스케줄러 종료] 작업 완료.");
    }

    /**
     * 만료된 요청 목록을 조회하고, 개별적으로 트랜잭션을 걸어 삭제를 진행합니다.
     */
    public void processExpiredRequests(LocalDateTime now) {
        // Repository에 findAllWithUserByExpiredDateBefore 메서드가 구현되어 있어야 합니다 (Fetch Join 권장)
        List<Request> expiredRequests = requestRepository.findAllWithUserByExpiredDateBefore(now);

        if (expiredRequests.isEmpty()) {
            return;
        }

        log.info("총 {}건의 만료된 계정을 발견했습니다. 삭제 처리를 시작합니다.", expiredRequests.size());

        RequestSchedulerService self = applicationContext.getBean(RequestSchedulerService.class);

        for (Request request : expiredRequests) {
            try {
                self.deleteExpiredRequest(request.getRequestId());
            } catch (Exception e) {
                log.error("계정 삭제 실패 (ID: {}). 다음 항목으로 넘어갑니다. 원인: {}", request.getRequestId(), e.getMessage());
            }
        }
    }

    /**
     * 핵심 로직: DB 삭제, 외부 연동 해제, 이벤트 발행
     * ★ 반드시 트랜잭션 내에서 실행되어야 하며, 성공 시에만 커밋됩니다.
     */
    @Transactional
    public void deleteExpiredRequest(Long requestId) {
        // 1. 트랜잭션 안에서 엔티티 재조회
        Request request = requestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found with ID: " + requestId));

        // 2. 중복 처리 방지
        if (request.getStatus() != Status.FULFILLED) {
            log.warn("이미 처리된 요청입니다. (ID: {}, Status: {})", requestId, request.getStatus());
            return;
        }

        log.info("삭제 프로세스 진행 중: {}", request.getUbuntuUsername());

        // 3. 외부 우분투 서버 계정 삭제 (실패 시 예외 발생 -> 전체 롤백)
        ubuntuAccountService.deleteUbuntuAccount(request.getUbuntuUsername());

        // 4. UsedId(UID/GID) 반환 및 연관된 Group 자동 삭제
        UsedId usedId = request.getUbuntuUid();
        if (usedId != null) {
            request.assignUbuntuUid(null); // 외래키 관계 끊기
            idAllocationService.releaseId(usedId); // ID 반환 (Group 삭제 포함)
        }

        // 5. Request 상태 변경 (Soft Delete)
        request.delete();

        // 6. 이벤트 발행
        eventPublisher.publishEvent(new RequestExpiredEvent(request.getUser()));

        log.info("계정 삭제 트랜잭션 커밋 대기: {}", request.getUbuntuUsername());
    }

    /**
     * 만료 임박 알림 전송 (읽기 전용)
     */
    @Transactional(readOnly = true)
    public void sendPreExpiryNotification(LocalDateTime targetDate, String dayLabel) {
        LocalDateTime start = targetDate.toLocalDate().atStartOfDay();
        LocalDateTime end = targetDate.toLocalDate().atTime(23, 59, 59);

        List<Request> requests = requestRepository.findAllByExpiresAtBetweenAndStatus(start, end, Status.FULFILLED);

        if (!requests.isEmpty()) {
            log.info("[{}] 후 만료 예정인 계정 {}건 알림 전송 시작.", dayLabel, requests.size());
        }

        for (Request request : requests) {
            try {
                User user = request.getUser();
                String serverName = "Unknown Server";

                if(request.getResourceGroup() != null) {
                    serverName = request.getResourceGroup().getServerName();
                }

                String subject = String.format("[DGU AI LAB] 서버 사용 만료 %s 전 안내", dayLabel);
                String message = String.format(
                        """
                        %s님의 서버 사용 기간이 %s 후 (%s) 만료될 예정입니다.
                        
                        - 계정: %s
                        - 서버: %s
                        
                        기간 연장이 필요하신 경우 관리자에게 문의 바랍니다.
                        별도 조치가 없을 시 계정은 자동 삭제됩니다.
                        """,
                        user.getName(), dayLabel, request.getExpiresAt().toLocalDate().toString(),
                        request.getUbuntuUsername(), serverName
                );

                alarmService.sendAllAlerts(user.getName(), user.getEmail(), subject, message);


            } catch (Exception e) {
                log.warn("{} 전 알림 전송 중 오류 발생 (ID: {}): {}", dayLabel, request.getRequestId(), e.getMessage());
            }
        }
    }
}