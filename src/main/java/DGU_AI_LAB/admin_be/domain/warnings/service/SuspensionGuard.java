package DGU_AI_LAB.admin_be.domain.warnings.service;

import DGU_AI_LAB.admin_be.domain.warnings.entity.UserSuspension;
import DGU_AI_LAB.admin_be.domain.warnings.repository.UserSuspensionRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;

/** 사용자가 지금 이용 정지 중인지 묻는 창구. 다른 영역(신청·재시작·포트 변경)은 정지의 내용을 모르고 이것만 쓴다. */
@Component
@RequiredArgsConstructor
public class SuspensionGuard {

    private final UserSuspensionRepository suspensionRepository;

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
}
