package DGU_AI_LAB.admin_be.domain.users.service;

import DGU_AI_LAB.admin_be.domain.alarm.service.AlarmService;
import DGU_AI_LAB.admin_be.domain.containerImage.entity.ContainerImage;
import DGU_AI_LAB.admin_be.domain.pod.repository.PodExternalPortRepository;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.requests.service.PodService;
import DGU_AI_LAB.admin_be.domain.requests.service.UbuntuAccountService;
import DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup;
import DGU_AI_LAB.admin_be.domain.users.dto.request.UserUpdateRequestDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.response.UserResponseDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.response.UserSummaryDTO;
import DGU_AI_LAB.admin_be.domain.users.entity.Role;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.ConflictException;
import DGU_AI_LAB.admin_be.error.exception.EntityNotFoundException;
import DGU_AI_LAB.admin_be.global.util.MessageUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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
    private PodService podService;

    @Mock
    private AlarmService alarmService;

    @Mock
    private PodExternalPortRepository podExternalPortRepository;

    @Mock
    private MessageUtils messageUtils;

    @Mock
    private TokenService tokenService;

    private User mockUser;

    @BeforeEach
    void setUp() {
        mockUser = User.builder()
                .email("test@dgu.ac.kr")
                .password("encodedPassword")
                .name("홍길동")
                .studentId("2021001234")
                .phone("010-1234-5678")
                .department("컴퓨터공학과")
                .build();
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

    /**
     * FULFILLED 상태이며 우분투 계정/Pod 정리 대상인 Request 목을 만든다.
     * deleteUser/deactivateUser 테스트가 공유한다.
     */
    private Request mockFulfilledRequest(String username, long requestId) {
        Request request = mock(Request.class);
        when(request.getStatus()).thenReturn(Status.FULFILLED);
        when(request.getUbuntuUsername()).thenReturn(username);
        when(request.getRequestId()).thenReturn(requestId);
        when(request.getPodName()).thenReturn("pod-" + username);
        return request;
    }

    private Request mockRequestWithStatus(Status status) {
        Request request = mock(Request.class);
        when(request.getStatus()).thenReturn(status);
        return request;
    }

    @Nested
    @DisplayName("deleteUser")
    class DeleteUser {

        @Test
        @DisplayName("연결된 Request가 없는 유저를 삭제하면 isActive를 false로 변경한다")
        void deleteUser_withNoRequests_softDeletes() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
            when(requestRepository.findAllByUser(mockUser)).thenReturn(List.of());
            when(messageUtils.get(anyString(), any(Object[].class))).thenReturn("mock");

            adminUserService.deleteUser(1L);

            assertThat(mockUser.getIsActive()).isFalse();
            assertThat(mockUser.getDeletedAt()).isNotNull();
            verifyNoInteractions(ubuntuAccountService, podService);
            verify(alarmService).sendAllAlerts(eq("홍길동"), eq("test@dgu.ac.kr"), anyString(), anyString());
        }

        @Test
        @DisplayName("유저를 삭제하면 남아있는 리프레시 토큰도 함께 폐기한다")
        void deleteUser_revokesRefreshToken() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
            when(requestRepository.findAllByUser(mockUser)).thenReturn(List.of());
            when(messageUtils.get(anyString(), any(Object[].class))).thenReturn("mock");

            adminUserService.deleteUser(1L);

            verify(tokenService).logout(1L);
        }

        @Test
        @DisplayName("존재하지 않는 유저를 삭제하려 하면 EntityNotFoundException을 던진다")
        void deleteUser_throwsException_whenUserNotFound() {
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> adminUserService.deleteUser(99L))
                    .isInstanceOf(EntityNotFoundException.class);
        }

        @Test
        @DisplayName("FULFILLED 상태 Request가 있으면 외부 계정 삭제 후 deleteAfterCleanup을 호출한다")
        void deleteUser_withFulfilledRequest_callsUbuntuDelete() {
            Request fulfilledRequest = mockFulfilledRequest("testuser", 1L);
            when(podExternalPortRepository.findByRequestRequestIdIn(anyList())).thenReturn(List.of());

            when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
            when(requestRepository.findAllByUser(mockUser)).thenReturn(List.of(fulfilledRequest));
            when(messageUtils.get(anyString(), any(Object[].class))).thenReturn("mock");

            adminUserService.deleteUser(1L);

            verify(podService).deletePod("pod-testuser");
            verify(ubuntuAccountService).deleteUbuntuAccount("testuser");
            verify(fulfilledRequest).deleteAfterCleanup();
            verify(alarmService).sendContainerDeletedEmail(eq(fulfilledRequest), anyList());
            assertThat(mockUser.getIsActive()).isFalse();
        }

        @Test
        @DisplayName("PENDING 상태 Request가 있으면 delete()를 호출한다 (외부 API 미호출)")
        void deleteUser_withPendingRequest_callsDelete() {
            Request pendingRequest = mockRequestWithStatus(Status.PENDING);

            when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
            when(requestRepository.findAllByUser(mockUser)).thenReturn(List.of(pendingRequest));
            when(messageUtils.get(anyString(), any(Object[].class))).thenReturn("mock");

            adminUserService.deleteUser(1L);

            verify(pendingRequest).delete();
            verifyNoInteractions(ubuntuAccountService, podService);
        }

        @Test
        @DisplayName("DELETED 상태 Request는 아무 처리도 하지 않는다")
        void deleteUser_withDeletedRequest_skips() {
            Request deletedRequest = mockRequestWithStatus(Status.DELETED);

            when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
            when(requestRepository.findAllByUser(mockUser)).thenReturn(List.of(deletedRequest));
            when(messageUtils.get(anyString(), any(Object[].class))).thenReturn("mock");

            adminUserService.deleteUser(1L);

            verify(deletedRequest, never()).delete();
            verify(deletedRequest, never()).deleteAfterCleanup();
            verifyNoInteractions(ubuntuAccountService, podService);
        }

        @Test
        @DisplayName("여러 상태의 Request가 혼합되면 각각 적절히 처리한다")
        void deleteUser_withMixedRequests_handlesEachCorrectly() {
            Request fulfilled = mockFulfilledRequest("fuser", 10L);
            when(podExternalPortRepository.findByRequestRequestIdIn(anyList())).thenReturn(List.of());

            Request pending = mockRequestWithStatus(Status.PENDING);
            Request deleted = mockRequestWithStatus(Status.DELETED);

            when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
            when(requestRepository.findAllByUser(mockUser)).thenReturn(List.of(fulfilled, pending, deleted));
            when(messageUtils.get(anyString(), any(Object[].class))).thenReturn("mock");

            adminUserService.deleteUser(1L);

            verify(podService).deletePod("pod-fuser");
            verify(ubuntuAccountService).deleteUbuntuAccount("fuser");
            verify(fulfilled).deleteAfterCleanup();
            verify(alarmService).sendContainerDeletedEmail(eq(fulfilled), anyList());
            verify(pending).delete();
            verify(deleted, never()).delete();
            verify(deleted, never()).deleteAfterCleanup();
        }

        @Test
        @DisplayName("MIGRATING 상태 Request가 있으면 삭제 자체를 거부하고 유저를 건드리지 않는다")
        void deleteUser_withMigratingRequest_rejectsAndLeavesUserUntouched() {
            Request migrating = mockRequestWithStatus(Status.MIGRATING);
            Request fulfilled = mock(Request.class);

            when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
            when(requestRepository.findAllByUser(mockUser)).thenReturn(List.of(migrating, fulfilled));

            assertThatThrownBy(() -> adminUserService.deleteUser(1L))
                    .isInstanceOf(ConflictException.class)
                    .extracting(e -> ((ConflictException) e).getErrorCode())
                    .isEqualTo(ErrorCode.REQUEST_MIGRATION_IN_PROGRESS);

            assertThat(mockUser.getIsActive()).isNotEqualTo(false);
            verify(fulfilled, never()).deleteAfterCleanup();
            verifyNoInteractions(ubuntuAccountService, podService, tokenService);
        }

        @Test
        @DisplayName("FULFILLED 요청이 여러 개여도 포트 배치 쿼리는 1회만 실행된다")
        void deleteUser_multipleFullfilledRequests_batchQueriesPorts() {
            Request req1 = mockFulfilledRequest("user1", 1L);
            Request req2 = mockFulfilledRequest("user2", 2L);
            Request req3 = mockFulfilledRequest("user3", 3L);

            when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
            when(requestRepository.findAllByUser(mockUser)).thenReturn(List.of(req1, req2, req3));
            when(podExternalPortRepository.findByRequestRequestIdIn(anyList())).thenReturn(List.of());
            when(messageUtils.get(anyString(), any(Object[].class))).thenReturn("mock");

            adminUserService.deleteUser(1L);

            // 개별 쿼리(findByRequestRequestId) 미호출, 배치 쿼리 1회만 호출
            verify(podExternalPortRepository, never()).findByRequestRequestId(any());
            verify(podExternalPortRepository, times(1)).findByRequestRequestIdIn(anyList());
            verify(alarmService, times(3)).sendContainerDeletedEmail(any(Request.class), anyList());
            verify(podService, times(3)).deletePod(anyString());
        }

        @Test
        @DisplayName("컨테이너 삭제 안내 메일 발송이 실패해도 계정 삭제와 탈퇴는 계속 진행된다")
        void deleteUser_continuesCleanup_whenContainerEmailFails() {
            Request req1 = mockFulfilledRequest("user1", 1L);
            Request req2 = mockFulfilledRequest("user2", 2L);

            when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
            when(requestRepository.findAllByUser(mockUser)).thenReturn(List.of(req1, req2));
            when(podExternalPortRepository.findByRequestRequestIdIn(anyList())).thenReturn(List.of());
            when(messageUtils.get(anyString(), any(Object[].class))).thenReturn("mock");
            doThrow(new RuntimeException("메일 서버 오류"))
                    .when(alarmService).sendContainerDeletedEmail(eq(req1), anyList());

            adminUserService.deleteUser(1L);

            verify(podService).deletePod("pod-user1");
            verify(podService).deletePod("pod-user2");
            verify(ubuntuAccountService).deleteUbuntuAccount("user1");
            verify(ubuntuAccountService).deleteUbuntuAccount("user2");
            verify(req1).deleteAfterCleanup();
            verify(req2).deleteAfterCleanup();
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

        @Test
        @DisplayName("연결된 Request가 없는 유저를 비활성화하면 isActive가 false, deletedAt은 null로 유지된다")
        void deactivateUser_withNoRequests_success() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
            when(requestRepository.findAllByUser(mockUser)).thenReturn(List.of());
            when(messageUtils.get(anyString(), any(Object[].class))).thenReturn("mock");

            UserSummaryDTO result = adminUserService.deactivateUser(1L);

            assertThat(mockUser.getIsActive()).isFalse();
            assertThat(mockUser.getDeletedAt()).isNull();
            assertThat(result.isActive()).isFalse();
            verifyNoInteractions(ubuntuAccountService, podService);
            verify(alarmService).sendAllAlerts(eq("홍길동"), eq("test@dgu.ac.kr"), anyString(), anyString());
        }

        @Test
        @DisplayName("존재하지 않는 유저면 EntityNotFoundException을 던진다")
        void deactivateUser_throwsWhenUserNotFound() {
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> adminUserService.deactivateUser(99L))
                    .isInstanceOf(EntityNotFoundException.class);
        }

        @Test
        @DisplayName("이미 비활성화된 유저면 ConflictException을 던진다")
        void deactivateUser_throwsWhenAlreadyInactive() {
            mockUser.withdraw();
            when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));

            assertThatThrownBy(() -> adminUserService.deactivateUser(1L))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining(ErrorCode.USER_ALREADY_INACTIVE.getMessage());
        }

        @Test
        @DisplayName("유저를 비활성화하면 남아있는 리프레시 토큰도 함께 폐기한다")
        void deactivateUser_revokesRefreshToken() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
            when(requestRepository.findAllByUser(mockUser)).thenReturn(List.of());
            when(messageUtils.get(anyString(), any(Object[].class))).thenReturn("mock");

            adminUserService.deactivateUser(1L);

            verify(tokenService).logout(1L);
        }

        @Test
        @DisplayName("FULFILLED 상태 Request가 있으면 deleteUser와 동일하게 외부 계정을 삭제한다")
        void deactivateUser_withFulfilledRequest_callsUbuntuDelete() {
            Request fulfilledRequest = mockFulfilledRequest("testuser", 1L);
            when(podExternalPortRepository.findByRequestRequestIdIn(anyList())).thenReturn(List.of());

            when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
            when(requestRepository.findAllByUser(mockUser)).thenReturn(List.of(fulfilledRequest));
            when(messageUtils.get(anyString(), any(Object[].class))).thenReturn("mock");

            adminUserService.deactivateUser(1L);

            verify(podService).deletePod("pod-testuser");
            verify(ubuntuAccountService).deleteUbuntuAccount("testuser");
            verify(fulfilledRequest).deleteAfterCleanup();
            verify(alarmService).sendContainerDeletedEmail(eq(fulfilledRequest), anyList());
            assertThat(mockUser.getIsActive()).isFalse();
            assertThat(mockUser.getDeletedAt()).isNull();
        }

        @Test
        @DisplayName("PENDING 상태 Request가 있으면 delete()를 호출한다 (외부 API 미호출)")
        void deactivateUser_withPendingRequest_callsDelete() {
            Request pendingRequest = mockRequestWithStatus(Status.PENDING);

            when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
            when(requestRepository.findAllByUser(mockUser)).thenReturn(List.of(pendingRequest));
            when(messageUtils.get(anyString(), any(Object[].class))).thenReturn("mock");

            adminUserService.deactivateUser(1L);

            verify(pendingRequest).delete();
            verifyNoInteractions(ubuntuAccountService, podService);
        }

        @Test
        @DisplayName("MIGRATING 상태 Request가 있으면 비활성화 자체를 거부하고 유저를 건드리지 않는다")
        void deactivateUser_withMigratingRequest_rejectsAndLeavesUserUntouched() {
            Request migrating = mockRequestWithStatus(Status.MIGRATING);

            when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
            when(requestRepository.findAllByUser(mockUser)).thenReturn(List.of(migrating));

            assertThatThrownBy(() -> adminUserService.deactivateUser(1L))
                    .isInstanceOf(ConflictException.class)
                    .extracting(e -> ((ConflictException) e).getErrorCode())
                    .isEqualTo(ErrorCode.REQUEST_MIGRATION_IN_PROGRESS);

            assertThat(mockUser.getIsActive()).isTrue();
            verifyNoInteractions(ubuntuAccountService, podService, tokenService);
        }

        @Test
        @DisplayName("DELETED 상태 Request는 아무 처리도 하지 않는다")
        void deactivateUser_withDeletedRequest_skips() {
            Request deletedRequest = mockRequestWithStatus(Status.DELETED);

            when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
            when(requestRepository.findAllByUser(mockUser)).thenReturn(List.of(deletedRequest));
            when(messageUtils.get(anyString(), any(Object[].class))).thenReturn("mock");

            adminUserService.deactivateUser(1L);

            verify(deletedRequest, never()).delete();
            verify(deletedRequest, never()).deleteAfterCleanup();
            verifyNoInteractions(ubuntuAccountService, podService);
        }

        @Test
        @DisplayName("여러 상태의 Request가 혼합되면 각각 적절히 처리한다")
        void deactivateUser_withMixedRequests_handlesEachCorrectly() {
            Request fulfilled = mockFulfilledRequest("fuser", 10L);
            when(podExternalPortRepository.findByRequestRequestIdIn(anyList())).thenReturn(List.of());

            Request pending = mockRequestWithStatus(Status.PENDING);
            Request deleted = mockRequestWithStatus(Status.DELETED);

            when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
            when(requestRepository.findAllByUser(mockUser)).thenReturn(List.of(fulfilled, pending, deleted));
            when(messageUtils.get(anyString(), any(Object[].class))).thenReturn("mock");

            adminUserService.deactivateUser(1L);

            verify(podService).deletePod("pod-fuser");
            verify(ubuntuAccountService).deleteUbuntuAccount("fuser");
            verify(fulfilled).deleteAfterCleanup();
            verify(alarmService).sendContainerDeletedEmail(eq(fulfilled), anyList());
            verify(pending).delete();
            verify(deleted, never()).delete();
            verify(deleted, never()).deleteAfterCleanup();
            assertThat(mockUser.getIsActive()).isFalse();
        }

        @Test
        @DisplayName("FULFILLED 요청이 여러 개여도 포트 배치 쿼리는 1회만 실행된다")
        void deactivateUser_multipleFulfilledRequests_batchQueriesPorts() {
            Request req1 = mockFulfilledRequest("user1", 1L);
            Request req2 = mockFulfilledRequest("user2", 2L);
            Request req3 = mockFulfilledRequest("user3", 3L);

            when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
            when(requestRepository.findAllByUser(mockUser)).thenReturn(List.of(req1, req2, req3));
            when(podExternalPortRepository.findByRequestRequestIdIn(anyList())).thenReturn(List.of());
            when(messageUtils.get(anyString(), any(Object[].class))).thenReturn("mock");

            adminUserService.deactivateUser(1L);

            verify(podExternalPortRepository, never()).findByRequestRequestId(any());
            verify(podExternalPortRepository, times(1)).findByRequestRequestIdIn(anyList());
            verify(alarmService, times(3)).sendContainerDeletedEmail(any(Request.class), anyList());
            verify(ubuntuAccountService, times(3)).deleteUbuntuAccount(anyString());
            verify(podService, times(3)).deletePod(anyString());
        }

        @Test
        @DisplayName("컨테이너 삭제 안내 메일 발송이 실패해도 계정 삭제와 비활성화는 계속 진행된다")
        void deactivateUser_continuesCleanup_whenContainerEmailFails() {
            Request req1 = mockFulfilledRequest("user1", 1L);
            Request req2 = mockFulfilledRequest("user2", 2L);

            when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
            when(requestRepository.findAllByUser(mockUser)).thenReturn(List.of(req1, req2));
            when(podExternalPortRepository.findByRequestRequestIdIn(anyList())).thenReturn(List.of());
            when(messageUtils.get(anyString(), any(Object[].class))).thenReturn("mock");
            doThrow(new RuntimeException("메일 서버 오류"))
                    .when(alarmService).sendContainerDeletedEmail(eq(req1), anyList());

            adminUserService.deactivateUser(1L);

            verify(podService).deletePod("pod-user1");
            verify(podService).deletePod("pod-user2");
            verify(ubuntuAccountService).deleteUbuntuAccount("user1");
            verify(ubuntuAccountService).deleteUbuntuAccount("user2");
            verify(req1).deleteAfterCleanup();
            verify(req2).deleteAfterCleanup();
            verify(alarmService).sendContainerDeletedEmail(eq(req2), anyList());
            assertThat(mockUser.getIsActive()).isFalse();
        }

        @Test
        @DisplayName("최종 비활성화 안내 메일 발송이 실패해도 비활성화 자체는 완료된다")
        void deactivateUser_completes_whenFinalNotificationEmailFails() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
            when(requestRepository.findAllByUser(mockUser)).thenReturn(List.of());
            when(messageUtils.get(anyString(), any(Object[].class))).thenReturn("mock");
            doThrow(new RuntimeException("Slack/메일 발송 실패"))
                    .when(alarmService).sendAllAlerts(anyString(), anyString(), anyString(), anyString());

            UserSummaryDTO result = adminUserService.deactivateUser(1L);

            assertThat(mockUser.getIsActive()).isFalse();
            assertThat(result.isActive()).isFalse();
            verify(tokenService).logout(1L);
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

    @Nested
    @DisplayName("deleteUbuntuAccount (단독 엔드포인트용)")
    class DeleteUbuntuAccount {

        private Request buildFulfilledRequest() {
            ResourceGroup rg = ResourceGroup.builder()
                    .resourceGroupName("Server A")
                    .description("desc")
                    .serverName("server-01")
                    .build();
            ContainerImage image = ContainerImage.builder()
                    .imageName("pytorch")
                    .imageVersion("2.1.0")
                    .cudaVersion("11.8")
                    .description("desc")
                    .build();
            Request req = Request.builder()
                    .ubuntuUsername("testuser")
                    .ubuntuPassword("pw")
                    .volumeSizeGiB(50L)
                    .expiresAt(LocalDateTime.now().plusDays(30))
                    .usagePurpose("연구")
                    .formAnswers("{}")
                    .user(mockUser)
                    .resourceGroup(rg)
                    .containerImage(image)
                    .build();
            req.approve(image, rg, 100L, null);
            return req;
        }

        @Test
        @DisplayName("FULFILLED Request가 있으면 외부 API 호출 후 DB 상태를 DELETED로 변경한다")
        void deleteUbuntuAccount_success() {
            Request request = buildFulfilledRequest();
            when(requestRepository.findByUbuntuUsername("testuser")).thenReturn(Optional.of(request));

            adminUserService.deleteUbuntuAccount("testuser");

            verify(podService).deletePod(null);
            verify(ubuntuAccountService).deleteUbuntuAccount("testuser");
            assertThat(request.getStatus()).isEqualTo(Status.DELETED);
            verify(alarmService).sendContainerDeletedEmail(request);
        }

        @Test
        @DisplayName("해당 username의 Request가 없으면 EntityNotFoundException을 던진다")
        void deleteUbuntuAccount_throwsWhenNotFound() {
            when(requestRepository.findByUbuntuUsername("nobody")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> adminUserService.deleteUbuntuAccount("nobody"))
                    .isInstanceOf(EntityNotFoundException.class);

            verifyNoInteractions(ubuntuAccountService, podService);
        }
    }
}
