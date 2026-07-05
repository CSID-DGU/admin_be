package DGU_AI_LAB.admin_be.domain.scheduler;

import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
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

    private static final int INACTIVE_MONTHS = 3;
    private static final int HARD_DELETE_YEARS = 1;

    // 매일 오전 09:00 실행
    @Scheduled(cron = "0 0 9 * * ?", zone = "Asia/Seoul")
    public void runUserLifecycleScheduler() {
        log.info("👤 [스케줄러 시작] 사용자 계정 수명주기 관리");
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));

        processInactiveUsers(now);
        processHardDeleteUsers(now);

        log.info("👤 [스케줄러 종료]");
    }

    private void processInactiveUsers(LocalDateTime now) {
        LocalDateTime searchThreshold = now.plusDays(8).minusMonths(INACTIVE_MONTHS);
        List<User> inactiveCandidates = userRepository.findInactiveUsers(searchThreshold);

        for (User user : inactiveCandidates) {
            try {
                // 유저별 독립 트랜잭션으로 처리 — H-7(LazyInit), H-11(롤백 전파) 방지
                userLifecycleService.processInactiveUser(user.getUserId(), now);
            } catch (Exception e) {
                log.error("유저({}) 수명주기 처리 중 오류: {}", user.getEmail(), e.getMessage());
            }
        }
    }

    private void processHardDeleteUsers(LocalDateTime now) {
        LocalDateTime hardDeleteThreshold = now.minusYears(HARD_DELETE_YEARS);
        List<User> hardDeleteTargets = userRepository.findUsersForHardDelete(hardDeleteThreshold);

        for (User user : hardDeleteTargets) {
            try {
                // 유저별 독립 트랜잭션으로 처리 — processInactiveUsers 결과에 영향 없음
                userLifecycleService.hardDeleteUser(user.getUserId(), user.getEmail());
            } catch (Exception e) {
                log.error("Hard Delete 실패: {}", user.getEmail(), e);
            }
        }
    }
}
