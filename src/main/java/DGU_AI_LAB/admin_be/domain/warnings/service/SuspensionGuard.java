package DGU_AI_LAB.admin_be.domain.warnings.service;

import DGU_AI_LAB.admin_be.domain.warnings.entity.UserSuspension;
import DGU_AI_LAB.admin_be.domain.warnings.repository.UserSuspensionRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;

/** 사용자가 지금 이용 정지 중인지 묻는 창구. 다른 영역(신청·재시작·포트 변경)은 정지의 내용을 모르고 이것만 쓴다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class SuspensionGuard {

    private final UserSuspensionRepository suspensionRepository;
    private final AccessEnforcementService accessEnforcementService;

    /** 이용 정지가 끝나는 시각. 정지 중이 아니면 비어 있다. */
    public Optional<LocalDateTime> suspendedUntil(Long userId) {
        return suspensionRepository
                .findFirstByUser_UserIdAndEndsAtAfterOrderByEndsAtDesc(userId, LocalDateTime.now())
                .map(UserSuspension::getEndsAt);
    }

    public boolean isSuspended(Long userId) {
        return suspendedUntil(userId).isPresent();
    }

    /** 정지 중이면 거절한다. */
    public void requireNotSuspended(Long userId) {
        if (isSuspended(userId)) {
            throw new BusinessException(ErrorCode.USER_SUSPENDED);
        }
    }

    /**
     * 사용자 행을 잠근 트랜잭션 안에서 정지 중이면 거절한다. 경고 부여도 같은 행을 잠그므로, 이 확인을 통과한 뒤에는
     * 트랜잭션이 끝날 때까지 정지가 새로 생기지 않는다. 잠금 읽기라 잠금을 기다리는 사이 부여된 정지도 본다.
     */
    public void requireNotSuspendedLocked(Long userId) {
        if (!suspensionRepository.findActiveForShare(userId, LocalDateTime.now()).isEmpty()) {
            throw new BusinessException(ErrorCode.USER_SUSPENDED);
        }
    }

    /**
     * 접속 포트를 새로 만드는 작업(생성·재시작·노드 이동·포트 변경)이 끝난 뒤 부른다. 작업이 도는 사이 정지가
     * 시작됐으면 그 포트는 열린 채로 만들어졌으므로 한 번 더 막는다. 실패해도 끝난 작업의 결과는 그대로 둔다 —
     * 주기 점검이 정지 중인 계정을 매번 다시 막는다.
     */
    public void reblockAfterPortsCreated(Long userId) {
        try {
            if (isSuspended(userId)) {
                accessEnforcementService.reapply(userId);
            }
        } catch (Exception e) {
            log.error("[access] 새 접속 포트에 이용 정지를 다시 걸지 못함 - 주기 점검이 다시 건다: userId={}", userId, e);
        }
    }
}
