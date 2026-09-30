package DGU_AI_LAB.admin_be.domain.users.service;

import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import DGU_AI_LAB.admin_be.error.exception.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminPasswordResetServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RequestRepository requestRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private UbuntuPasswordSyncClient ubuntuPasswordSyncClient;
    @Mock private TokenService tokenService;
    @InjectMocks private AdminPasswordResetService service;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .email("test@dgu.ac.kr")
                .password("encodedPassword")
                .name("홍길동")
                .studentId("2021001234")
                .phone("010-1234-5678")
                .department("컴퓨터공학과")
                .build();
    }

    private void withAccount() {
        user.assignUbuntuAccount(21000L, 21000L);
        ReflectionTestUtils.setField(user, "ubuntuUsername", "honggildong");
    }

    @Test
    @DisplayName("리눅스 계정이 없으면 컨테이너 반영 없이 웹 비밀번호와 SSH 해시를 함께 바꾸고 세션을 끊는다")
    void reset_withoutAccount_updatesBothHashes() {
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("newPassword1!")).thenReturn("newEncodedPw");

        service.resetPassword(1L, "newPassword1!");

        assertThat(user.getPassword()).isEqualTo("newEncodedPw");
        assertThat(user.getUbuntuPasswordHash()).startsWith("$6$").doesNotContain("newPassword1!");
        verifyNoInteractions(ubuntuPasswordSyncClient);
        verify(tokenService).logout(1L);
    }

    @Test
    @DisplayName("리눅스 계정이 있으면 컨테이너에 먼저 반영하고 같은 해시를 저장한다")
    void reset_withAccount_appliesToContainersFirst() {
        withAccount();
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("newPassword1!")).thenReturn("newEncodedPw");

        service.resetPassword(1L, "newPassword1!");

        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(ubuntuPasswordSyncClient).apply(eq("honggildong"), hash.capture());
        assertThat(user.getUbuntuPasswordHash()).isEqualTo(hash.getValue());
        assertThat(user.getPassword()).isEqualTo("newEncodedPw");
        verify(tokenService).logout(1L);
    }

    @Test
    @DisplayName("컨테이너 반영이 실패하면 웹 비밀번호도 SSH 해시도 바꾸지 않고 세션도 그대로 둔다")
    void reset_syncFailure_changesNothing() {
        withAccount();
        user.changeUbuntuPasswordHash("$6$old$hash");
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        doThrow(new BusinessException(ErrorCode.UBUNTU_PASSWORD_CHANGE_FAILED))
                .when(ubuntuPasswordSyncClient).apply(eq("honggildong"), anyString());

        assertThatThrownBy(() -> service.resetPassword(1L, "newPassword1!"))
                .isInstanceOf(BusinessException.class);
        assertThat(user.getPassword()).isEqualTo("encodedPassword");
        assertThat(user.getUbuntuPasswordHash()).isEqualTo("$6$old$hash");
        verify(tokenService, never()).logout(anyLong());
    }

    @Test
    @DisplayName("컨테이너 생성 중이면 409로 막고 아무것도 바꾸지 않는다")
    void reset_whileProvisioning_isRejected() {
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(requestRepository.existsByUser_UserIdAndStatus(1L, Status.PROCESSING)).thenReturn(true);

        assertThatThrownBy(() -> service.resetPassword(1L, "newPassword1!"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.UBUNTU_PASSWORD_CHANGE_WHILE_PROVISIONING);
        assertThat(user.getPassword()).isEqualTo("encodedPassword");
        verifyNoInteractions(ubuntuPasswordSyncClient, tokenService);
    }

    @Test
    @DisplayName("없는 사용자면 404")
    void reset_unknownUser_isNotFound() {
        when(userRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resetPassword(99L, "newPassword1!"))
                .isInstanceOf(EntityNotFoundException.class);
        verifyNoInteractions(ubuntuPasswordSyncClient, tokenService);
    }
}
