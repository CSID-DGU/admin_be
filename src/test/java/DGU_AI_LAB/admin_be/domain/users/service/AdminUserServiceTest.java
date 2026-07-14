package DGU_AI_LAB.admin_be.domain.users.service;

import DGU_AI_LAB.admin_be.domain.alarm.service.AlarmService;
import DGU_AI_LAB.admin_be.domain.containerImage.entity.ContainerImage;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.requests.service.UbuntuAccountService;
import DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup;
import DGU_AI_LAB.admin_be.domain.users.dto.request.UserUpdateRequestDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.response.UserResponseDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.response.UserSummaryDTO;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.ConflictException;
import DGU_AI_LAB.admin_be.error.exception.EntityNotFoundException;
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
    private AlarmService alarmService;

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
        @DisplayName("유저가 없으면 EntityNotFoundException을 던진다")
        void updateUser_throwsException_whenUserNotFound() {
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            UserUpdateRequestDTO request = new UserUpdateRequestDTO("newPw", false);

            assertThatThrownBy(() -> adminUserService.updateUser(99L, request))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("deleteUser")
    class DeleteUser {

        @Test
        @DisplayName("연결된 Request가 없는 유저를 삭제하면 isActive를 false로 변경한다")
        void deleteUser_withNoRequests_softDeletes() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
            when(requestRepository.findAllByUser(mockUser)).thenReturn(List.of());

            adminUserService.deleteUser(1L);

            assertThat(mockUser.getIsActive()).isFalse();
            verifyNoInteractions(ubuntuAccountService);
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
            Request fulfilledRequest = mock(Request.class);
            when(fulfilledRequest.getStatus()).thenReturn(Status.FULFILLED);
            when(fulfilledRequest.getUbuntuUsername()).thenReturn("testuser");

            when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
            when(requestRepository.findAllByUser(mockUser)).thenReturn(List.of(fulfilledRequest));

            adminUserService.deleteUser(1L);

            verify(ubuntuAccountService).deleteUbuntuAccount("testuser");
            verify(fulfilledRequest).deleteAfterCleanup();
            verify(alarmService).sendContainerDeletedEmail(fulfilledRequest);
            assertThat(mockUser.getIsActive()).isFalse();
        }

        @Test
        @DisplayName("PENDING 상태 Request가 있으면 delete()를 호출한다 (외부 API 미호출)")
        void deleteUser_withPendingRequest_callsDelete() {
            Request pendingRequest = mock(Request.class);
            when(pendingRequest.getStatus()).thenReturn(Status.PENDING);

            when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
            when(requestRepository.findAllByUser(mockUser)).thenReturn(List.of(pendingRequest));

            adminUserService.deleteUser(1L);

            verify(pendingRequest).delete();
            verifyNoInteractions(ubuntuAccountService);
        }

        @Test
        @DisplayName("DELETED 상태 Request는 아무 처리도 하지 않는다")
        void deleteUser_withDeletedRequest_skips() {
            Request deletedRequest = mock(Request.class);
            when(deletedRequest.getStatus()).thenReturn(Status.DELETED);

            when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
            when(requestRepository.findAllByUser(mockUser)).thenReturn(List.of(deletedRequest));

            adminUserService.deleteUser(1L);

            verify(deletedRequest, never()).delete();
            verify(deletedRequest, never()).deleteAfterCleanup();
            verifyNoInteractions(ubuntuAccountService);
        }

        @Test
        @DisplayName("여러 상태의 Request가 혼합되면 각각 적절히 처리한다")
        void deleteUser_withMixedRequests_handlesEachCorrectly() {
            Request fulfilled = mock(Request.class);
            when(fulfilled.getStatus()).thenReturn(Status.FULFILLED);
            when(fulfilled.getUbuntuUsername()).thenReturn("fuser");

            Request pending = mock(Request.class);
            when(pending.getStatus()).thenReturn(Status.PENDING);

            Request deleted = mock(Request.class);
            when(deleted.getStatus()).thenReturn(Status.DELETED);

            when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
            when(requestRepository.findAllByUser(mockUser)).thenReturn(List.of(fulfilled, pending, deleted));

            adminUserService.deleteUser(1L);

            verify(ubuntuAccountService).deleteUbuntuAccount("fuser");
            verify(fulfilled).deleteAfterCleanup();
            verify(alarmService).sendContainerDeletedEmail(fulfilled);
            verify(pending).delete();
            verify(deleted, never()).delete();
            verify(deleted, never()).deleteAfterCleanup();
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
                    .ubuntuPasswordBase64("base64pw")
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

            verifyNoInteractions(ubuntuAccountService);
        }
    }
}
