package DGU_AI_LAB.admin_be.domain.scheduler;

import DGU_AI_LAB.admin_be.domain.users.entity.UbuntuAccountStatus;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import DGU_AI_LAB.admin_be.domain.users.service.UbuntuAccountReleaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 회수 중(RELEASING)인 우분투 계정을 주기적으로 한 단계씩 진행한다. 단계는 {@link UbuntuAccountReleaseService} 참고. */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountRevokeJobPoller {

    private final UserRepository userRepository;
    private final UbuntuAccountReleaseService ubuntuAccountReleaseService;

    @Scheduled(fixedDelayString = "${operations.revoke.poll-ms:3000}")
    public void pollAccountRevokes() {
        for (User user : userRepository.findAllByUbuntuAccountStatus(UbuntuAccountStatus.RELEASING)) {
            try {
                ubuntuAccountReleaseService.advance(user.getUserId());
            } catch (Exception e) {
                // 한 사용자의 실패가 나머지 사용자 처리를 막지 않게 한다. 다음 바퀴에 다시 본다.
                log.warn("[계정 회수] 진행 실패 - userId={}", user.getUserId(), e);
            }
        }
    }
}
