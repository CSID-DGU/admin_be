package DGU_AI_LAB.admin_be.domain.warnings.service;

import DGU_AI_LAB.admin_be.domain.warnings.entity.UserSuspension;
import DGU_AI_LAB.admin_be.domain.warnings.repository.UserSuspensionRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SuspensionGuardTest {

    private static final Long USER_ID = 7L;

    @Mock private UserSuspensionRepository suspensionRepository;
    @Mock private AccessEnforcementService accessEnforcementService;
    @InjectMocks private SuspensionGuard guard;

    private void suspended(boolean suspended) {
        UserSuspension suspension = mock(UserSuspension.class);
        if (suspended) {
            when(suspension.getEndsAt()).thenReturn(LocalDateTime.now().plusDays(7));
        }
        when(suspensionRepository.findFirstByUser_UserIdAndEndsAtAfterOrderByEndsAtDesc(eq(USER_ID), any()))
                .thenReturn(suspended ? Optional.of(suspension) : Optional.empty());
    }

    @Test
    @DisplayName("잠금 안의 확인은 잠금 읽기로 본 정지가 있으면 거절한다")
    void lockedCheckRefusesWhenSuspended() {
        when(suspensionRepository.findActiveForShare(eq(USER_ID), any()))
                .thenReturn(List.of(mock(UserSuspension.class)));

        assertThatThrownBy(() -> guard.requireNotSuspendedLocked(USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_SUSPENDED);
    }

    @Test
    @DisplayName("잠금 안의 확인은 정지가 없으면 통과한다")
    void lockedCheckPassesWhenNotSuspended() {
        when(suspensionRepository.findActiveForShare(eq(USER_ID), any())).thenReturn(List.of());

        assertThatCode(() -> guard.requireNotSuspendedLocked(USER_ID)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("정지 중이면 새 접속 포트에 차단을 다시 건다")
    void reblocksWhenSuspended() {
        suspended(true);

        guard.reblockAfterPortsCreated(USER_ID);

        verify(accessEnforcementService).reapply(USER_ID);
    }

    @Test
    @DisplayName("정지 중이 아니면 접속 작업을 등록하지 않는다")
    void leavesAccessAloneWhenNotSuspended() {
        suspended(false);

        guard.reblockAfterPortsCreated(USER_ID);

        verifyNoInteractions(accessEnforcementService);
    }

    @Test
    @DisplayName("다시 거는 데 실패해도 끝난 작업의 결과를 깨뜨리지 않는다")
    void reblockFailureDoesNotPropagate() {
        suspended(true);
        doThrow(new IllegalStateException("config-server down")).when(accessEnforcementService).reapply(USER_ID);

        assertThatCode(() -> guard.reblockAfterPortsCreated(USER_ID)).doesNotThrowAnyException();
    }
}
