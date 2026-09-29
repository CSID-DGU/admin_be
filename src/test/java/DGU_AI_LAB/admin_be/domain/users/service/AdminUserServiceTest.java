package DGU_AI_LAB.admin_be.domain.users.service;

import DGU_AI_LAB.admin_be.domain.alarm.service.AlarmService;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.requests.service.PodMigrationService;
import DGU_AI_LAB.admin_be.domain.requests.service.RequestExpiryService;
import DGU_AI_LAB.admin_be.domain.requests.service.UbuntuAccountService;
import DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup;
import DGU_AI_LAB.admin_be.domain.users.dto.request.UserUpdateRequestDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.response.UserResponseDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.response.UserSummaryDTO;
import DGU_AI_LAB.admin_be.domain.users.entity.Role;
import DGU_AI_LAB.admin_be.domain.users.entity.UbuntuAccountStatus;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import DGU_AI_LAB.admin_be.error.exception.ConflictException;
import DGU_AI_LAB.admin_be.error.exception.EntityNotFoundException;
import DGU_AI_LAB.admin_be.global.util.MessageUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @InjectMocks
    private AdminUserService adminUserService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RequestRepository requestRepository;

    @Mock
    private UbuntuAccountService ubuntuAccountService;

    @Mock
    private RequestExpiryService requestExpiryService;

    @Mock
    private PodMigrationService podMigrationService;

    @Mock
    private AlarmService alarmService;

    @Mock
    private MessageUtils messageUtils;

    @Mock
    private TokenService tokenService;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private TransactionStatus transactionStatus;

    private User mockUser;
    private long nextRequestId = 1000L;

    @BeforeEach
    void setUp() {
        mockUser = User.builder()
                .email("test@dgu.ac.kr")
                .password("encodedPassword")
                .name("홍길동")
                .studentId("2021001234")
                .phone("010-1234-5678")
                .department("컴퓨터공학과")
                .ubuntuUsername("testuser")
                .build();
        // 승인을 한 번이라도 받은 사용자는 웹 계정에 리눅스 계정(UID/GID)이 물려 있다.
        // 이 계정 회수는 요청 만료가 아니라 사용자 삭제/비활성화에서만 일어난다.
        mockUser.assignUbuntuAccount(20001L, 20001L);
        lenient().when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        // 계정 회수 단계는 User 행을 잠그고 수행한다.
        lenient().when(userRepository.findByIdForUpdate(any())).thenReturn(Optional.of(mockUser));
    }

    @Nested
    @DisplayName("getAllUsers")
    class GetAllUsers {

        @Test
        @DisplayName("유저 목록이 있으면 UserSummaryDTO 리스트를 반환한다")
        void getAllUsers_returnsUserList() {
            User user2 = User.builder()
                    .email("user2@dgu.ac.kr")
                    .password("pw")
                    .name("이순신")
                    .studentId("2021005678")
                    .phone("010-5678-1234")
                    .department("전자공학과")
                    .build();

            when(userRepository.findAll()).thenReturn(List.of(mockUser, user2));

            List<UserSummaryDTO> result = adminUserService.getAllUsers();

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("유저가 없으면 빈 리스트를 반환한다")
        void getAllUsers_returnsEmptyList_whenNoUsers() {
            when(userRepository.findAll()).thenReturn(List.of());

            List<UserSummaryDTO> result = adminUserService.getAllUsers();

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("updateUser")
    class UpdateUser {

        @Test
        @DisplayName("유저가 존재하면 정보를 수정하고 UserResponseDTO를 반환한다")
        void updateUser_success() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));

            UserUpdateRequestDTO request = new UserUpdateRequestDTO("newPw", false);
            UserResponseDTO result = adminUserService.updateUser(1L, request);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("계정을 비활성화하면 리프레시 토큰도 함께 폐기한다")
        void updateUser_revokesRefreshToken_whenDeactivated() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));

            adminUserService.updateUser(1L, new UserUpdateRequestDTO("newPw", false));

            verify(tokenService).logout(1L);
        }

        @Test
        @DisplayName("계정이 활성 상태로 유지되면 리프레시 토큰을 건드리지 않는다")
        void updateUser_keepsRefreshToken_whenStillActive() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));

            adminUserService.updateUser(1L, new UserUpdateRequestDTO("newPw", true));

            verify(tokenService, never()).logout(anyLong());
        }

        @Test
        @DisplayName("유저가 없으면 EntityNotFoundException을 던진다")
        void updateUser_throwsException_whenUserNotFound() {
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            UserUpdateRequestDTO request = new UserUpdateRequestDTO("newPw", false);

            assertThatThrownBy(() -> adminUserService.updateUser(99L, request))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    private Request mockRequest(Status status) {
        Request request = mock(Request.class);
        AtomicReference<Status> current = new AtomicReference<>(status);
        lenient().when(request.getStatus()).thenAnswer(inv -> current.get());
        lenient().doAnswer(inv -> { current.set(Status.DELETED); return null; }).when(request).delete();
        lenient().doAnswer(inv -> { current.set(Status.FULFILLED); return null; }).when(request).endMigration();
        // Long을 돌려주는 메서드의 mock 기본값은 0이라, 작업 번호가 없는 상태를 명시한다.
        lenient().when(request.getJobId()).thenReturn(null);
        long requestId = nextRequestId++;
        lenient().when(request.getRequestId()).thenReturn(requestId);
        lenient().when(requestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));
        return request;
    }

    private void givenRequests(Request... requests) {
        when(requestRepository.findAllByUser(any())).thenReturn(List.of(requests));
    }

    @Nested
    @DisplayName("deleteUser")
    class DeleteUser {

        @BeforeEach
        void setUp() {
            lenient().when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        }

        @Test
        @DisplayName("웹 계정은 바로 탈퇴 처리하고, 우분투 계정은 회수 중(RELEASING)으로 바꾼다 — 계정 회수는 폴러가 마무리한다")
        void withdrawsAndStartsAccountRelease() {
            givenRequests();

            adminUserService.deleteUser(1L);

            assertThat(mockUser.getIsActive()).isFalse();
            assertThat(mockUser.getDeletedAt()).isNotNull();
            assertThat(mockUser.getUbuntuAccountStatus()).isEqualTo(UbuntuAccountStatus.RELEASING);
            verify(tokenService).logout(1L);
            verifyNoInteractions(ubuntuAccountService);
        }

        @Test
        @DisplayName("우분투 계정이 배정된 적 없는 유저는 계정 상태를 바꾸지 않는다")
        void userWithoutAccountStaysNone() {
            User noAccount = User.builder().email("n@dgu.ac.kr").password("pw").name("무계정").build();
            when(userRepository.findById(2L)).thenReturn(Optional.of(noAccount));
            when(userRepository.findByIdForUpdate(any())).thenReturn(Optional.of(noAccount));
            givenRequests();

            adminUserService.deleteUser(2L);

            assertThat(noAccount.getUbuntuAccountStatus()).isEqualTo(UbuntuAccountStatus.NONE);
            assertThat(noAccount.getIsActive()).isFalse();
        }

        @Test
        @DisplayName("존재하지 않는 유저면 EntityNotFoundException을 던진다")
        void missingUser() {
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> adminUserService.deleteUser(99L)).isInstanceOf(EntityNotFoundException.class);
        }

        @Test
        @DisplayName("상태별로 처리한다: FULFILLED는 회수 시작, PENDING·DENIED는 논리 삭제, EXPIRING·DELETED는 그대로")
        void handlesEachStatus() {
            Request fulfilled = mockRequest(Status.FULFILLED);
            Request pending = mockRequest(Status.PENDING);
            Request denied = mockRequest(Status.DENIED);
            Request expiring = mockRequest(Status.EXPIRING);
            Request deleted = mockRequest(Status.DELETED);
            givenRequests(fulfilled, pending, denied, expiring, deleted);

            adminUserService.deleteUser(1L);

            verify(requestExpiryService).startContainerRevoke(fulfilled.getRequestId());
            verify(requestExpiryService, times(1)).startContainerRevoke(anyLong());
            verify(pending).delete();
            verify(denied).delete();
            verify(expiring, never()).delete();
            verify(deleted, never()).delete();
        }

        @Test
        @DisplayName("이전 회수 주기의 계정 회수 작업 번호를 비워 모든 노드를 새로 등록하게 한다")
        void forgetsPreviousAccountRevokeJobs() {
            Request oldWithJob = mockRequest(Status.DELETED);
            when(oldWithJob.getJobId()).thenReturn(4242L);
            Request oldWithoutJob = mockRequest(Status.DELETED);
            givenRequests(oldWithJob, oldWithoutJob);

            adminUserService.deleteUser(1L);

            verify(oldWithJob).forgetAccountRevokeJob();
            verify(oldWithoutJob, never()).forgetAccountRevokeJob();
        }

        @Test
        @DisplayName("MIGRATING·PROCESSING 신청이 있으면 아무것도 건드리지 않고 거부한다")
        void rejectsInFlight() {
            for (Status inFlight : List.of(Status.MIGRATING, Status.PROCESSING)) {
                Request fulfilled = mockRequest(Status.FULFILLED);
                givenRequests(fulfilled, mockRequest(inFlight));

                assertThatThrownBy(() -> adminUserService.deleteUser(1L)).isInstanceOf(ConflictException.class);

                assertThat(mockUser.getIsActive()).isTrue();
                assertThat(mockUser.getUbuntuAccountStatus()).isEqualTo(UbuntuAccountStatus.ACTIVE);
                verify(requestExpiryService, never()).startContainerRevoke(anyLong());
            }
        }

        @Test
        @DisplayName("MIGRATING 신청은 끝난 결과를 먼저 반영해 본다 — 반영 직후 틈 때문에 정리가 거부되지 않게")
        void settlesFinishedMigrationFirst() {
            Request migrating = mockRequest(Status.MIGRATING);
            Long migratingId = migrating.getRequestId();
            doAnswer(inv -> {
                migrating.endMigration();
                return null;
            }).when(podMigrationService).settleFinishedMigration(migratingId);
            givenRequests(migrating);

            adminUserService.deleteUser(1L);

            verify(requestExpiryService).startContainerRevoke(migrating.getRequestId());
        }

        @Test
        @DisplayName("한 컨테이너의 회수 등록이 실패해도 나머지는 계속 시작하고, 알린 뒤 부분 실패로 끝낸다 — 탈퇴는 하지 않는다")
        void partialRegistrationFailure() {
            Request failing = mockRequest(Status.FULFILLED);
            Request ok = mockRequest(Status.FULFILLED);
            givenRequests(failing, ok);
            when(requestExpiryService.startContainerRevoke(failing.getRequestId()))
                    .thenThrow(new BusinessException(ErrorCode.POD_DELETION_FAILED));

            assertThatThrownBy(() -> adminUserService.deleteUser(1L))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_REQUEST_CLEANUP_PARTIALLY_FAILED);

            verify(requestExpiryService).startContainerRevoke(ok.getRequestId());
            verify(alarmService).sendSlackAlert(contains("컨테이너 회수 등록 실패"), isNull());
            assertThat(mockUser.getIsActive()).isTrue();
            // 계정 회수는 컨테이너가 남은 동안 기다리므로 RELEASING으로 둔다. 관리자가 다시 누르면 이어서 한다.
            assertThat(mockUser.getUbuntuAccountStatus()).isEqualTo(UbuntuAccountStatus.RELEASING);
        }

        @Test
        @DisplayName("탈퇴 안내 메일 발송이 실패해도 탈퇴는 완료된다")
        void mailFailureDoesNotFail() {
            givenRequests();
            when(messageUtils.get(anyString())).thenThrow(new RuntimeException("mail down"));

            adminUserService.deleteUser(1L);

            assertThat(mockUser.getIsActive()).isFalse();
        }
    }

    @Nested
    @DisplayName("reactivateUser")
    class ReactivateUser {

        @Test
        @DisplayName("비활성화된 유저를 재활성화하면 isActive가 true, deletedAt이 null이 된다")
        void reactivateUser_success() {
            mockUser.withdraw();
            when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));

            UserSummaryDTO result = adminUserService.reactivateUser(1L);

            assertThat(mockUser.getIsActive()).isTrue();
            assertThat(mockUser.getDeletedAt()).isNull();
            assertThat(result.isActive()).isTrue();
        }

        @Test
        @DisplayName("존재하지 않는 유저면 EntityNotFoundException을 던진다")
        void reactivateUser_throwsWhenUserNotFound() {
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> adminUserService.reactivateUser(99L))
                    .isInstanceOf(EntityNotFoundException.class);
        }

        @Test
        @DisplayName("이미 활성화된 유저면 ConflictException을 던진다")
        void reactivateUser_throwsWhenAlreadyActive() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));

            assertThatThrownBy(() -> adminUserService.reactivateUser(1L))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining(ErrorCode.USER_ALREADY_ACTIVE.getMessage());
        }
    }


    @Nested
    @DisplayName("deactivateUser")
    class DeactivateUser {

        @BeforeEach
        void setUp() {
            lenient().when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        }

        @Test
        @DisplayName("비활성화하면 isActive가 false, deletedAt은 null로 유지되고 컨테이너·계정 회수를 시작한다")
        void deactivatesAndStartsRevoke() {
            Request fulfilled = mockRequest(Status.FULFILLED);
            givenRequests(fulfilled);

            UserSummaryDTO result = adminUserService.deactivateUser(1L);

            assertThat(result.isActive()).isFalse();
            assertThat(result.ubuntuAccountStatus()).isEqualTo("RELEASING");
            assertThat(mockUser.getDeletedAt()).isNull();
            verify(requestExpiryService).startContainerRevoke(fulfilled.getRequestId());
            verify(tokenService).logout(1L);
        }

        @Test
        @DisplayName("존재하지 않는 유저면 EntityNotFoundException을 던진다")
        void missingUser() {
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> adminUserService.deactivateUser(99L)).isInstanceOf(EntityNotFoundException.class);
        }

        @Test
        @DisplayName("이미 비활성화된 유저면 ConflictException을 던지고 아무것도 회수하지 않는다")
        void alreadyInactive() {
            mockUser.deactivate();

            assertThatThrownBy(() -> adminUserService.deactivateUser(1L)).isInstanceOf(ConflictException.class);
            verifyNoInteractions(requestExpiryService);
        }

        @Test
        @DisplayName("PROCESSING 신청이 있으면 비활성화 자체를 거부한다")
        void rejectsInFlight() {
            givenRequests(mockRequest(Status.PROCESSING));

            assertThatThrownBy(() -> adminUserService.deactivateUser(1L)).isInstanceOf(ConflictException.class);
            assertThat(mockUser.getIsActive()).isTrue();
        }
    }

    @Nested
    @DisplayName("deleteUbuntuAccountOfUser")
    class DeleteUbuntuAccountOfUser {

        @BeforeEach
        void setUp() {
            lenient().when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        }

        @Test
        @DisplayName("컨테이너 회수를 시작하고 계정을 RELEASING으로 바꾼다 — 웹 계정은 그대로 둔다")
        void startsRelease() {
            Request fulfilled = mockRequest(Status.FULFILLED);
            givenRequests(fulfilled);

            adminUserService.deleteUbuntuAccountOfUser(1L);

            verify(requestExpiryService).startContainerRevoke(fulfilled.getRequestId());
            assertThat(mockUser.getUbuntuAccountStatus()).isEqualTo(UbuntuAccountStatus.RELEASING);
            assertThat(mockUser.getIsActive()).isTrue();
        }

        @Test
        @DisplayName("회수 중인 계정에 다시 부르면 실패한 컨테이너 회수와 계정 회수를 다시 등록한다")
        void rerunWhileReleasing() {
            mockUser.beginUbuntuAccountRelease();
            Request revertedAfterFailure = mockRequest(Status.FULFILLED);
            Request failedNode = mockRequest(Status.DELETED);
            when(failedNode.getJobId()).thenReturn(77L);
            givenRequests(revertedAfterFailure, failedNode);

            adminUserService.deleteUbuntuAccountOfUser(1L);

            verify(requestExpiryService).startContainerRevoke(revertedAfterFailure.getRequestId());
            verify(failedNode).forgetAccountRevokeJob();
            assertThat(mockUser.getUbuntuAccountStatus()).isEqualTo(UbuntuAccountStatus.RELEASING);
        }

        @Test
        @DisplayName("계정이 없으면(NONE) 404")
        void noAccount() {
            User noAccount = User.builder().email("n@dgu.ac.kr").password("pw").name("무계정").build();
            when(userRepository.findById(2L)).thenReturn(Optional.of(noAccount));

            assertThatThrownBy(() -> adminUserService.deleteUbuntuAccountOfUser(2L))
                    .isInstanceOf(EntityNotFoundException.class);
            verifyNoInteractions(requestExpiryService);
        }
    }

    @Nested
    @DisplayName("changeUserRole")
    class ChangeUserRole {

        @Test
        @DisplayName("USER를 ADMIN으로 변경한다")
        void changeUserRole_userToAdmin_success() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));

            UserSummaryDTO result = adminUserService.changeUserRole(1L, Role.ADMIN);

            assertThat(mockUser.getRole()).isEqualTo(Role.ADMIN);
            assertThat(result.role()).isEqualTo("ADMIN");
        }

        @Test
        @DisplayName("존재하지 않는 유저면 EntityNotFoundException을 던진다")
        void changeUserRole_throwsWhenUserNotFound() {
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> adminUserService.changeUserRole(99L, Role.ADMIN))
                    .isInstanceOf(EntityNotFoundException.class);
        }

        @Test
        @DisplayName("이미 같은 권한이면 ConflictException을 던진다")
        void changeUserRole_throwsWhenAlreadyHasRole() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));

            assertThatThrownBy(() -> adminUserService.changeUserRole(1L, Role.USER))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining(ErrorCode.USER_ALREADY_HAS_ROLE.getMessage());
        }
    }

}
