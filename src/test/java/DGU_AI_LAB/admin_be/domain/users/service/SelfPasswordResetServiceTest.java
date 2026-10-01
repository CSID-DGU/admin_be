package DGU_AI_LAB.admin_be.domain.users.service;

import DGU_AI_LAB.admin_be.domain.users.dto.response.PasswordResetSummaryDTO;
import DGU_AI_LAB.admin_be.domain.users.entity.PasswordHashes;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import DGU_AI_LAB.admin_be.global.util.EmailSendThrottle;
import DGU_AI_LAB.admin_be.global.util.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SelfPasswordResetServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private EmailSendThrottle emailSendThrottle;
    @Mock private EmailService emailService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private PasswordResetService passwordResetService;
    @Mock private PasswordResetNotifier notifier;
    @InjectMocks private SelfPasswordResetService service;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .email("Test@dgu.ac.kr")
                .password("encodedPassword")
                .name("홍길동")
                .studentId("2021001234")
                .phone("010-1234-5678")
                .department("컴퓨터공학과")
                .build();
        ReflectionTestUtils.setField(user, "userId", 1L);
    }

    private static PasswordResetSummaryDTO summary() {
        return new PasswordResetSummaryDTO(12L, 1L, "홍길동", "Test@dgu.ac.kr", "honggildong", "PENDING", null, null);
    }

    @Test
    @DisplayName("가입된 활성 계정이면 소문자로 맞춘 주소에 코드를 묶어 가입한 주소로 보낸다")
    void requestCode_registered_sendsCode() {
        when(userRepository.findByEmail("test@dgu.ac.kr")).thenReturn(Optional.of(user));

        service.requestCode(" Test@DGU.ac.kr ");

        verify(emailSendThrottle).acquire("test@dgu.ac.kr");
        verify(emailService).sendPasswordResetCode("test@dgu.ac.kr", "Test@dgu.ac.kr");
    }

    @Test
    @DisplayName("가입되지 않은 주소면 오류 없이 끝내고 메일을 보내지 않되 발송 횟수는 센다")
    void requestCode_unregistered_isSilent() {
        when(userRepository.findByEmail("nobody@dgu.ac.kr")).thenReturn(Optional.empty());

        assertThatCode(() -> service.requestCode("nobody@dgu.ac.kr")).doesNotThrowAnyException();

        verify(emailSendThrottle).acquire("nobody@dgu.ac.kr");
        verifyNoInteractions(emailService);
    }

    @Test
    @DisplayName("비활성 계정이면 메일을 보내지 않는다")
    void requestCode_inactive_isSilent() {
        ReflectionTestUtils.setField(user, "isActive", false);
        when(userRepository.findByEmail("test@dgu.ac.kr")).thenReturn(Optional.of(user));

        service.requestCode("test@dgu.ac.kr");

        verifyNoInteractions(emailService);
    }

    @Test
    @DisplayName("발송 제한에 걸리면 사용자 조회도 하지 않는다")
    void requestCode_throttled_doesNotLookUp() {
        doThrow(new BusinessException(ErrorCode.TOO_MANY_EMAIL_SENDS)).when(emailSendThrottle).acquire("test@dgu.ac.kr");

        assertThatThrownBy(() -> service.requestCode("test@dgu.ac.kr")).isInstanceOf(BusinessException.class);

        verifyNoInteractions(userRepository, emailService);
    }

    @Test
    @DisplayName("코드를 소모한 뒤 해시로만 신청을 내고 관리자에게 알린다 — 평문은 넘기지 않는다")
    void submit_createsRequestAndNotifiesAdmins() {
        when(userRepository.findByEmail("test@dgu.ac.kr")).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("newPassword1!")).thenReturn("newEncodedPw");
        when(passwordResetService.submit(eq(1L), any())).thenReturn(new PasswordResetService.Submission(summary(), true));

        service.submit("Test@dgu.ac.kr", "123456", "newPassword1!");

        InOrder order = inOrder(emailService, passwordResetService, notifier);
        order.verify(emailService).consumePasswordResetCode("test@dgu.ac.kr", "123456");
        ArgumentCaptor<PasswordHashes> hashes = ArgumentCaptor.forClass(PasswordHashes.class);
        order.verify(passwordResetService).submit(eq(1L), hashes.capture());
        order.verify(notifier).requested(summary());
        assertThat(hashes.getValue().web()).isEqualTo("newEncodedPw");
        assertThat(hashes.getValue().ubuntu()).startsWith("$6$").doesNotContain("newPassword1!");
    }

    @Test
    @DisplayName("승인 전에 다시 낸 신청은 관리자에게 또 알리지 않는다")
    void submit_replacement_doesNotNotifyAgain() {
        when(userRepository.findByEmail("test@dgu.ac.kr")).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("newPassword1!")).thenReturn("newEncodedPw");
        when(passwordResetService.submit(eq(1L), any())).thenReturn(new PasswordResetService.Submission(summary(), false));

        service.submit("test@dgu.ac.kr", "123456", "newPassword1!");

        verifyNoInteractions(notifier);
    }

    @Test
    @DisplayName("코드가 틀리면 DB를 조회하지 않고 신청도 내지 않는다")
    void submit_wrongCode_touchesNothing() {
        doThrow(new BusinessException(ErrorCode.INVALID_AUTH_CODE))
                .when(emailService).consumePasswordResetCode("test@dgu.ac.kr", "000000");

        assertThatThrownBy(() -> service.submit("test@dgu.ac.kr", "000000", "newPassword1!"))
                .isInstanceOf(BusinessException.class);

        verifyNoInteractions(userRepository, passwordResetService, notifier);
    }

    @Test
    @DisplayName("코드를 받은 뒤 비활성화된 계정이면 코드 오류와 같은 응답으로 거절한다")
    void submit_inactiveUser_isRejectedAsInvalidCode() {
        ReflectionTestUtils.setField(user, "isActive", false);
        when(userRepository.findByEmail("test@dgu.ac.kr")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.submit("test@dgu.ac.kr", "123456", "newPassword1!"))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_AUTH_CODE));

        verifyNoInteractions(passwordResetService);
    }

    @Test
    @DisplayName("앞선 신청을 반영하는 중이면 그 오류를 그대로 돌려주고 알리지 않는다")
    void submit_whileProcessing_propagates() {
        when(userRepository.findByEmail("test@dgu.ac.kr")).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("newPassword1!")).thenReturn("newEncodedPw");
        when(passwordResetService.submit(eq(1L), any()))
                .thenThrow(new BusinessException(ErrorCode.PASSWORD_RESET_IN_PROGRESS));

        assertThatThrownBy(() -> service.submit("test@dgu.ac.kr", "123456", "newPassword1!"))
                .isInstanceOf(BusinessException.class);

        verifyNoInteractions(notifier);
    }
}
