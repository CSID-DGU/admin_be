package DGU_AI_LAB.admin_be.domain.scheduler;

import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import DGU_AI_LAB.admin_be.domain.users.service.AdminUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserSchedulerService {

    private final UserRepository userRepository;
    private final UserLifecycleTransactionalService userLifecycleService;
    private final AdminUserService adminUserService;

    // D-7 경고까지 포함하려면 (strict <) 기준일을 7+1=8일 앞당겨야 한다
    private static final int NOTIFICATION_LEAD_DAYS = 8;

    // 매일 오전 09:00 실행
    @Scheduled(cron = "0 0 9 * * ?", zone = "Asia/Seoul")
    public void runUserLifecycleScheduler() {
        log.info("👤 [스케줄러 시작] 사용자 계정 수명주기 관리");
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));

        processInactiveUsers(now);

        log.info("👤 [스케줄러 종료]");
    }

    private void processInactiveUsers(LocalDateTime now) {
        LocalDateTime searchThreshold = now.plusDays(NOTIFICATION_LEAD_DAYS)
                .minusMonths(UserLifecycleTransactionalService.INACTIVE_MONTHS);
        List<User> inactiveCandidates = userRepository.findInactiveUsers(searchThreshold, Status.openStatuses());

        for (User user : inactiveCandidates) {
            try {
                // 유저별 독립 트랜잭션으로 처리 — H-7(LazyInit), H-11(롤백 전파) 방지
                if (userLifecycleService.processInactiveUser(user.getUserId(), now)) {
                    // 관리자 탈퇴와 같은 경로로 컨테이너·우분투 계정 회수까지 시작한다. 진행 중인 신청이 있어
                    // 거부되면 다음 날 다시 시도된다.
                    adminUserService.withdrawInactiveUser(user.getUserId());
                }
            } catch (Exception e) {
                log.error("유저({}) 수명주기 처리 중 오류: {}", user.getUserId(), e.getMessage());
            }
        }
    }
}
