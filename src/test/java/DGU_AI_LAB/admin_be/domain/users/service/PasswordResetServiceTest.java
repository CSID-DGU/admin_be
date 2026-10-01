package DGU_AI_LAB.admin_be.domain.users.service;

import DGU_AI_LAB.admin_be.domain.requests.dto.request.PasswordChangeRegisterRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.job.JobClient;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.users.dto.response.PasswordResetSummaryDTO;
import DGU_AI_LAB.admin_be.domain.users.entity.PasswordHashes;
import DGU_AI_LAB.admin_be.domain.users.entity.PasswordResetRequest;
import DGU_AI_LAB.admin_be.domain.users.entity.PasswordResetStatus;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.repository.PasswordResetRequestRepository;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import DGU_AI_LAB.admin_be.error.exception.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PasswordResetServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long ADMIN_ID = 9L;
    private static final Long RESET_ID = 12L;
    private static final PasswordHashes NEW = new PasswordHashes("newEncodedPw", "$6$new$hash");

    @Mock private UserRepository userRepository;
    @Mock private RequestRepository requestRepository;
    @Mock private PasswordResetRequestRepository resetRepository;
    @Mock private JobClient jobClient;
    @Mock private TokenService tokenService;
    @Mock private UserLoginService userLoginService;
    @Mock private PasswordResetNotifier notifier;
    @Mock private PlatformTransactionManager transactionManager;
    @InjectMocks private PasswordResetService service;

    private User user;
    private User admin;

    @BeforeEach
    void setUp() {
        user = newUser("test@dgu.ac.kr", USER_ID);
        user.changeUbuntuPasswordHash("$6$old$hash");
        admin = newUser("admin@dgu.ac.kr", ADMIN_ID);
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
        when(userRepository.getReferenceById(ADMIN_ID)).thenReturn(admin);
        when(resetRepository.save(any(PasswordResetRequest.class))).thenAnswer(invocation -> {
            PasswordResetRequest saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "passwordResetRequestId", RESET_ID);
            return saved;
        });
    }

    private static User newUser(String email, Long id) {
        User created = User.builder()
                .email(email)
                .password("encodedPassword")
                .name("홍길동")
                .studentId("2021001234")
                .phone("010-1234-5678")
                .department("컴퓨터공학과")
                .ubuntuUsername("honggildong")
                .build();
        ReflectionTestUtils.setField(created, "userId", id);
        return created;
    }

    private void withAccount() {
        user.assignUbuntuAccount(21000L, 21000L);
    }

    /** 저장소에 있는 승인 대기 신청. */
    private PasswordResetRequest pendingReset() {
        PasswordResetRequest reset = PasswordResetRequest.pending(user, NEW);
        ReflectionTestUtils.setField(reset, "passwordResetRequestId", RESET_ID);
        when(resetRepository.findUserIdById(RESET_ID)).thenReturn(Optional.of(USER_ID));
        when(resetRepository.findByIdForUpdate(RESET_ID)).thenReturn(Optional.of(reset));
        return reset;
    }

    private PasswordResetRequest processingReset() {
        PasswordResetRequest reset = pendingReset();
        reset.startProcessing(admin, 77L);
        return reset;
    }

    private void assertUserUnchanged() {
        assertThat(user.getPassword()).isEqualTo("encodedPassword");
        assertThat(user.getUbuntuPasswordHash()).isEqualTo("$6$old$hash");
        verifyNoInteractions(tokenService, userLoginService);
    }

    @Nested
    @DisplayName("submit")
    class Submit {

        @Test
        @DisplayName("열린 신청이 없으면 승인 대기 신청을 만들고, 비밀번호는 아직 바꾸지 않는다")
        void createsPendingRequest() {
            when(resetRepository.findAllByUser_UserIdAndStatusIn(anyLong(), anyCollection())).thenReturn(List.of());

            PasswordResetService.Submission submission = service.submit(USER_ID, NEW);

            assertThat(submission.created()).isTrue();
            assertThat(submission.request().passwordResetRequestId()).isEqualTo(RESET_ID);
            assertThat(submission.request().status()).isEqualTo("PENDING");
            assertThat(submission.request().email()).isEqualTo("test@dgu.ac.kr");
            assertUserUnchanged();
            verifyNoInteractions(jobClient, notifier);
        }

        @Test
        @DisplayName("승인 대기 중인 신청이 있으면 새로 만들지 않고 그 신청의 새 비밀번호만 바꾼다")
        void replacesPasswordOfPendingRequest() {
            PasswordResetRequest pending = PasswordResetRequest.pending(user, new PasswordHashes("first", "$6$first$hash"));
            ReflectionTestUtils.setField(pending, "passwordResetRequestId", RESET_ID);
            when(resetRepository.findAllByUser_UserIdAndStatusIn(anyLong(), anyCollection())).thenReturn(List.of(pending));

            PasswordResetService.Submission submission = service.submit(USER_ID, NEW);

            assertThat(submission.created()).isFalse();
            assertThat(pending.getPasswordHash()).isEqualTo("newEncodedPw");
            assertThat(pending.getUbuntuPasswordHash()).isEqualTo("$6$new$hash");
            verify(resetRepository, never()).save(any());
        }

        @Test
        @DisplayName("앞선 신청을 컨테이너에 반영하는 중이면 409로 막는다")
        void rejectsWhileProcessing() {
            PasswordResetRequest processing = PasswordResetRequest.pending(user, new PasswordHashes("first", "$6$first$hash"));
            processing.startProcessing(admin, 77L);
            when(resetRepository.findAllByUser_UserIdAndStatusIn(anyLong(), anyCollection())).thenReturn(List.of(processing));

            assertThatThrownBy(() -> service.submit(USER_ID, NEW))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PASSWORD_RESET_IN_PROGRESS);
            assertThat(processing.getPasswordHash()).isEqualTo("first");
        }

        @Test
        @DisplayName("없는 사용자면 404")
        void unknownUserIsNotFound() {
            when(userRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.submit(99L, NEW)).isInstanceOf(EntityNotFoundException.class);
            verify(resetRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("approve")
    class Approve {

        @Test
        @DisplayName("리눅스 계정이 있으면 작업만 등록하고 PROCESSING으로 둔다 — 비밀번호는 작업이 성공해야 바뀐다")
        void registersJobAndWaits() {
            withAccount();
            PasswordResetRequest reset = pendingReset();
            when(jobClient.registerPasswordChange(any())).thenReturn(77L);

            PasswordResetSummaryDTO result = service.approve(RESET_ID, ADMIN_ID);

            verify(jobClient).registerPasswordChange(
                    new PasswordChangeRegisterRequestDTO(RESET_ID, "honggildong", "$6$new$hash"));
            assertThat(result.status()).isEqualTo("PROCESSING");
            assertThat(reset.getStatus()).isEqualTo(PasswordResetStatus.PROCESSING);
            assertThat(reset.getJobId()).isEqualTo(77L);
            assertThat(reset.getReviewedBy()).isSameAs(admin);
            assertUserUnchanged();
            verifyNoInteractions(notifier);
        }

        @Test
        @DisplayName("리눅스 계정이 없으면 작업 없이 바로 적용하고 세션을 끊는다")
        void appliesImmediatelyWithoutAccount() {
            PasswordResetRequest reset = pendingReset();

            PasswordResetSummaryDTO result = service.approve(RESET_ID, ADMIN_ID);

            assertThat(result.status()).isEqualTo("APPLIED");
            assertThat(user.getPassword()).isEqualTo("newEncodedPw");
            assertThat(user.getUbuntuPasswordHash()).isEqualTo("$6$new$hash");
            assertThat(reset.getPasswordHash()).isNull();
            assertThat(reset.getUbuntuPasswordHash()).isNull();
            verifyNoInteractions(jobClient);
            verify(tokenService).logout(USER_ID);
            verify(userLoginService).clearFailedAttempts("test@dgu.ac.kr");
            verify(notifier).applied("test@dgu.ac.kr");
        }

        @Test
        @DisplayName("사용자 행을 먼저 잠근 뒤 신청을 잠그고, 그 뒤에 생성 중인 컨테이너를 확인한다")
        void locksUserBeforeReadingState() {
            withAccount();
            pendingReset();

            service.approve(RESET_ID, ADMIN_ID);

            InOrder order = inOrder(userRepository, resetRepository, requestRepository, jobClient);
            order.verify(userRepository).findByIdForUpdate(USER_ID);
            order.verify(resetRepository).findByIdForUpdate(RESET_ID);
            order.verify(requestRepository).existsByUser_UserIdAndStatus(USER_ID, Status.PROCESSING);
            order.verify(jobClient).registerPasswordChange(any());
        }

        @Test
        @DisplayName("컨테이너 생성 중이면 409로 막고 작업을 등록하지 않는다")
        void rejectsWhileProvisioning() {
            withAccount();
            PasswordResetRequest reset = pendingReset();
            when(requestRepository.existsByUser_UserIdAndStatus(USER_ID, Status.PROCESSING)).thenReturn(true);

            assertThatThrownBy(() -> service.approve(RESET_ID, ADMIN_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UBUNTU_PASSWORD_CHANGE_WHILE_PROVISIONING);
            assertThat(reset.getStatus()).isEqualTo(PasswordResetStatus.PENDING);
            verifyNoInteractions(jobClient);
            assertUserUnchanged();
        }

        @Test
        @DisplayName("작업 등록이 실패하면 신청은 승인 대기로 남고 아무것도 바뀌지 않는다")
        void registrationFailureLeavesPending() {
            withAccount();
            PasswordResetRequest reset = pendingReset();
            when(jobClient.registerPasswordChange(any()))
                    .thenThrow(new BusinessException(ErrorCode.UBUNTU_PASSWORD_CHANGE_FAILED));

            assertThatThrownBy(() -> service.approve(RESET_ID, ADMIN_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UBUNTU_PASSWORD_CHANGE_FAILED);
            assertThat(reset.getStatus()).isEqualTo(PasswordResetStatus.PENDING);
            assertThat(reset.getUbuntuPasswordHash()).isEqualTo("$6$new$hash");
            assertUserUnchanged();
            verify(transactionManager).rollback(any());
        }

        @Test
        @DisplayName("이미 반영 중인 신청은 다시 승인하지 못한다 — 작업을 두 번 등록하지 않는다")
        void rejectsProcessing() {
            withAccount();
            processingReset();

            assertThatThrownBy(() -> service.approve(RESET_ID, ADMIN_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PASSWORD_RESET_IN_PROGRESS);
            verifyNoInteractions(jobClient);
        }

        @Test
        @DisplayName("거절된 신청은 승인하지 못한다")
        void rejectsClosed() {
            PasswordResetRequest reset = pendingReset();
            reset.deny(admin);

            assertThatThrownBy(() -> service.approve(RESET_ID, ADMIN_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PASSWORD_RESET_ALREADY_CLOSED);
            assertUserUnchanged();
        }

        @Test
        @DisplayName("없는 신청이면 404")
        void unknownRequestIsNotFound() {
            when(resetRepository.findUserIdById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.approve(404L, ADMIN_ID))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PASSWORD_RESET_REQUEST_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("complete")
    class Complete {

        @Test
        @DisplayName("작업이 성공하면 웹 비밀번호와 SSH 해시를 함께 바꾸고 세션을 끊고 신청자에게 알린다")
        void appliesHashes() {
            withAccount();
            PasswordResetRequest reset = processingReset();

            service.complete(RESET_ID);

            assertThat(user.getPassword()).isEqualTo("newEncodedPw");
            assertThat(user.getUbuntuPasswordHash()).isEqualTo("$6$new$hash");
            assertThat(reset.getStatus()).isEqualTo(PasswordResetStatus.APPLIED);
            assertThat(reset.getPasswordHash()).isNull();
            assertThat(reset.getUbuntuPasswordHash()).isNull();
            verify(tokenService).logout(USER_ID);
            verify(userLoginService).clearFailedAttempts("test@dgu.ac.kr");
            verify(notifier).applied("test@dgu.ac.kr");
        }

        @Test
        @DisplayName("이미 반영된 신청이면 아무것도 하지 않는다")
        void ignoresAlreadyApplied() {
            withAccount();
            PasswordResetRequest reset = processingReset();
            reset.completeJob();

            service.complete(RESET_ID);

            assertUserUnchanged();
            verifyNoInteractions(notifier);
        }

        @Test
        @DisplayName("로그인 실패 횟수 삭제가 실패해도 적용은 끝나고 알림도 보낸다")
        void aftermathFailureDoesNotUndo() {
            withAccount();
            PasswordResetRequest reset = processingReset();
            doThrow(new IllegalStateException("redis down")).when(userLoginService).clearFailedAttempts(any());

            service.complete(RESET_ID);

            assertThat(reset.getStatus()).isEqualTo(PasswordResetStatus.APPLIED);
            verify(notifier).applied("test@dgu.ac.kr");
        }
    }

    @Nested
    @DisplayName("returnToPending")
    class ReturnToPending {

        @Test
        @DisplayName("반영 중이던 신청을 승인 대기로 되돌린다 — 새 비밀번호는 남겨 다시 승인할 수 있다")
        void returnsProcessingToPending() {
            withAccount();
            PasswordResetRequest reset = processingReset();

            Optional<PasswordResetSummaryDTO> returned = service.returnToPending(RESET_ID);

            assertThat(returned).isPresent();
            assertThat(returned.get().status()).isEqualTo("PENDING");
            assertThat(reset.getJobId()).isNull();
            assertThat(reset.getReviewedBy()).isNull();
            assertThat(reset.getUbuntuPasswordHash()).isEqualTo("$6$new$hash");
            assertUserUnchanged();
        }

        @Test
        @DisplayName("승인과 같은 순서(사용자 → 신청)로 잠근다")
        void locksUserBeforeReset() {
            withAccount();
            processingReset();

            service.returnToPending(RESET_ID);

            InOrder order = inOrder(userRepository, resetRepository);
            order.verify(userRepository).findByIdForUpdate(USER_ID);
            order.verify(resetRepository).findByIdForUpdate(RESET_ID);
        }

        @Test
        @DisplayName("반영 중이 아니면 건드리지 않는다")
        void ignoresOtherStates() {
            PasswordResetRequest reset = pendingReset();

            assertThat(service.returnToPending(RESET_ID)).isEmpty();
            assertThat(reset.getStatus()).isEqualTo(PasswordResetStatus.PENDING);
        }
    }

    @Nested
    @DisplayName("deny")
    class Deny {

        @Test
        @DisplayName("승인 대기 신청을 거절하면 새 비밀번호를 지우고 신청자에게 알린다")
        void deniesPending() {
            PasswordResetRequest reset = pendingReset();

            PasswordResetSummaryDTO result = service.deny(RESET_ID, ADMIN_ID);

            assertThat(result.status()).isEqualTo("DENIED");
            assertThat(reset.getPasswordHash()).isNull();
            assertThat(reset.getUbuntuPasswordHash()).isNull();
            assertThat(reset.getReviewedBy()).isSameAs(admin);
            assertUserUnchanged();
            verify(notifier).denied("test@dgu.ac.kr");
        }

        @Test
        @DisplayName("승인과 같은 순서(사용자 → 신청)로 잠가 동시에 눌러도 교착이 나지 않는다")
        void locksUserBeforeReset() {
            pendingReset();

            service.deny(RESET_ID, ADMIN_ID);

            InOrder order = inOrder(userRepository, resetRepository);
            order.verify(userRepository).findByIdForUpdate(USER_ID);
            order.verify(resetRepository).findByIdForUpdate(RESET_ID);
        }

        @Test
        @DisplayName("컨테이너에 반영하는 중이면 거절하지 못한다")
        void cannotDenyProcessing() {
            withAccount();
            processingReset();

            assertThatThrownBy(() -> service.deny(RESET_ID, ADMIN_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PASSWORD_RESET_IN_PROGRESS);
            verifyNoInteractions(notifier);
        }
    }

    @Test
    @DisplayName("처리할 신청 목록은 승인 대기와 반영 중만 담는다")
    void listsOpenRequests() {
        PasswordResetRequest reset = PasswordResetRequest.pending(user, NEW);
        ReflectionTestUtils.setField(reset, "passwordResetRequestId", RESET_ID);
        when(resetRepository.findAllWithUserByStatusIn(
                List.of(PasswordResetStatus.PENDING, PasswordResetStatus.PROCESSING))).thenReturn(List.of(reset));

        List<PasswordResetSummaryDTO> open = service.getOpenRequests();

        assertThat(open).extracting(PasswordResetSummaryDTO::passwordResetRequestId).containsExactly(RESET_ID);
        assertThat(open.get(0).ubuntuUsername()).isEqualTo("honggildong");
    }
}
