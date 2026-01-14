package DGU_AI_LAB.admin_be.domain.scheduler;

import DGU_AI_LAB.admin_be.domain.alarm.service.AlarmService;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import DGU_AI_LAB.admin_be.global.util.MessageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserSchedulerService {

    private final UserRepository userRepository;
    private final AlarmService alarmService;
    private final MessageUtils messageUtils;

    private static final int INACTIVE_MONTHS = 3;
    private static final int HARD_DELETE_YEARS = 1;

    // 매일 오전 09:00 실행
    @Scheduled(cron = "0 0 9 * * ?", zone = "Asia/Seoul")
    @Transactional
    public void runUserLifecycleScheduler() {
        log.info("👤 [스케줄러 시작] 사용자 계정 수명주기 관리");
        LocalDateTime now = LocalDateTime.now();

        // 1. Soft Delete 대상자 처리 (및 예고 알림)
        processInactiveUsers(now);

        // 2. Hard Delete 대상자 처리
        processHardDeleteUsers(now);

        log.info("👤 [스케줄러 종료]");
    }

    /**
     * 장기 미접속자 조회 및 처리 (경고 알림 OR Soft Delete)
     */
    private void processInactiveUsers(LocalDateTime now) {
        // 기준일: 오늘 - 3개월 + 7일 (7일 전 알림을 위해 여유 있게 조회 후 로직에서 필터링)
        // 사실상 3개월 전 즈음에 활동이 멈춘 사람들을 모두 가져옴
        LocalDateTime searchThreshold = now.minusMonths(INACTIVE_MONTHS).plusDays(8);
        List<User> inactiveCandidates = userRepository.findInactiveUsers(searchThreshold);

        for (User user : inactiveCandidates) {
            try {
                // 이 유저의 "활동 만료일(삭제 예정일)" 계산
                // 만료일 = Max(LastLogin, LastPodExpire) + 3개월
                LocalDateTime lastActivity = user.getLastLoginAt();

                // 쿼리에서 이미 필터링했지만, Java 단에서 정확한 D-Day 계산을 위해 다시 확인
                // Request는 Lazy Loading이므로 트랜잭션 내에서 접근 가능
                if (!user.getRequests().isEmpty()) {
                    LocalDateTime lastPodExpire = user.getRequests().stream()
                            .map(req -> req.getExpiresAt())
                            .max(LocalDateTime::compareTo)
                            .orElse(LocalDateTime.MIN);

                    if (lastPodExpire.isAfter(lastActivity)) {
                        lastActivity = lastPodExpire;
                    }
                }

                LocalDateTime deleteDate = lastActivity.plusMonths(INACTIVE_MONTHS);
                long daysLeft = ChronoUnit.DAYS.between(now.toLocalDate(), deleteDate.toLocalDate());

                // 1) 예고 알림 (D-7, D-3, D-1)
                if (daysLeft == 7 || daysLeft == 3 || daysLeft == 1) {
                    sendWarningAlert(user, daysLeft, deleteDate);
                }
                // 2) Soft Delete 실행 (D-Day 또는 그 이후)
                else if (daysLeft <= 0) {
                    softDeleteUser(user);
                }

            } catch (Exception e) {
                log.error("유저({}) 수명주기 처리 중 오류: {}", user.getEmail(), e.getMessage());
            }
        }
    }

    private void sendWarningAlert(User user, long daysLeft, LocalDateTime deleteDate) {
        String dateStr = deleteDate.toLocalDate().toString();

        String subject = messageUtils.get("notification.user.delete-warning.subject", String.valueOf(daysLeft));
        String body = messageUtils.get("notification.user.delete-warning.body",
                user.getName(), String.valueOf(daysLeft), dateStr);

        alarmService.sendAllAlerts(user.getName(), user.getEmail(), subject, body);
        log.info("경고 알림 발송: {} ({}일 전)", user.getEmail(), daysLeft);
    }

    private void softDeleteUser(User user) {
        user.withdraw(); // isActive = false, deletedAt = now

        String subject = messageUtils.get("notification.user.soft-delete.subject");
        String body = messageUtils.get("notification.user.soft-delete.body", user.getName());

        alarmService.sendAllAlerts(user.getName(), user.getEmail(), subject, body);
        log.info("계정 비활성화(Soft Delete) 완료: {}", user.getEmail());
    }

    /**
     * Hard Delete (개인정보 완전 삭제)
     */
    private void processHardDeleteUsers(LocalDateTime now) {
        LocalDateTime hardDeleteThreshold = now.minusYears(HARD_DELETE_YEARS);
        List<User> hardDeleteTargets = userRepository.findUsersForHardDelete(hardDeleteThreshold);

        for (User user : hardDeleteTargets) {
            try {
                Long userId = user.getUserId();
                String email = user.getEmail();

                // DB 완전 삭제 (Cascade 설정으로 인해 연관 Request도 삭제됨)
                userRepository.delete(user);

                log.info("계정 영구 삭제(Hard Delete) 완료: ID={}, Email={}", userId, email);
            } catch (Exception e) {
                log.error("Hard Delete 실패: {}", user.getEmail(), e);
            }
        }
    }
}