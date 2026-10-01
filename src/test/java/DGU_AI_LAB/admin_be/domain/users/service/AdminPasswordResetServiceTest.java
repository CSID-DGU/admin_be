package DGU_AI_LAB.admin_be.domain.users.service;

import DGU_AI_LAB.admin_be.domain.users.dto.response.PasswordResetSummaryDTO;
import DGU_AI_LAB.admin_be.domain.users.entity.PasswordHashes;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminPasswordResetServiceTest {

    @Mock private PasswordResetService passwordResetService;
    @Mock private PasswordEncoder passwordEncoder;
    @InjectMocks private AdminPasswordResetService service;

    private static PasswordResetSummaryDTO summary(String status) {
        return new PasswordResetSummaryDTO(12L, 5L, "홍길동", "test@dgu.ac.kr", "honggildong", status, null, null);
    }

    @Test
    @DisplayName("재설정 신청을 대신 내고 곧바로 승인한다 — 컨테이너 반영은 본인 신청과 같은 경로를 탄다")
    void submitsThenApproves() {
        when(passwordEncoder.encode("newPassword1!")).thenReturn("newEncodedPw");
        when(passwordResetService.submit(eq(5L), any()))
                .thenReturn(new PasswordResetService.Submission(summary("PENDING"), true));
        when(passwordResetService.approve(12L, 9L)).thenReturn(summary("PROCESSING"));

        PasswordResetSummaryDTO result = service.reset(5L, "newPassword1!", 9L);

        assertThat(result.status()).isEqualTo("PROCESSING");
        InOrder order = inOrder(passwordResetService);
        ArgumentCaptor<PasswordHashes> hashes = ArgumentCaptor.forClass(PasswordHashes.class);
        order.verify(passwordResetService).submit(eq(5L), hashes.capture());
        order.verify(passwordResetService).approve(12L, 9L);
        assertThat(hashes.getValue().web()).isEqualTo("newEncodedPw");
        assertThat(hashes.getValue().ubuntu()).startsWith("$6$").doesNotContain("newPassword1!");
    }

    @Test
    @DisplayName("신청을 내지 못하면 승인하지 않는다")
    void submitFailureSkipsApproval() {
        when(passwordEncoder.encode("newPassword1!")).thenReturn("newEncodedPw");
        when(passwordResetService.submit(eq(5L), any()))
                .thenThrow(new BusinessException(ErrorCode.PASSWORD_RESET_IN_PROGRESS));

        assertThatThrownBy(() -> service.reset(5L, "newPassword1!", 9L)).isInstanceOf(BusinessException.class);

        verify(passwordResetService, never()).approve(anyLong(), anyLong());
    }
}
