package DGU_AI_LAB.admin_be.domain.users.service;

import DGU_AI_LAB.admin_be.domain.containerImage.entity.ContainerImage;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.requests.service.PodService;
import DGU_AI_LAB.admin_be.domain.requests.service.UbuntuAccountService;
import DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup;
import DGU_AI_LAB.admin_be.domain.users.dto.request.UserUpdateRequestDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.response.UserResponseDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.response.UserSummaryDTO;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import DGU_AI_LAB.admin_be.error.exception.EntityNotFoundException;
import DGU_AI_LAB.admin_be.global.event.PodCleanupFailedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

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
    private PodService podService;

    @Mock
    private UbuntuAccountService ubuntuAccountService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

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

    private Request createFulfilledRequest(String username, String podName) {
        ResourceGroup rg = ResourceGroup.builder()
                .resourceGroupName("test-rg")
                .serverName("farm-server-01")
                .build();
        ContainerImage image = ContainerImage.builder()
                .imageName("pytorch")
                .imageVersion("2.0")
                .build();
        Request request = Request.builder()
                .ubuntuUsername(username)
                .ubuntuPassword("pw")
                .ubuntuPasswordBase64("cHc=")
                .volumeSizeGiB(100L)
                .expiresAt(LocalDateTime.now().plusDays(30))
                .usagePurpose("연구")
                .formAnswers("{}")
                .user(mockUser)
                .resourceGroup(rg)
                .containerImage(image)
                .build();
        request.approve(image, rg, 100L, null);
        request.assignPodInfo(podName, "node-1");
        return request;
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
                    .name("백동민")
                    .studentId("2021112505")
                    .phone("010-5678-1234")
                    .department("멀티미디어공학과")
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

            UserUpdateRequestDTO request = new UserUpdateRequestDTO("홍길동", "newPw", false);
            UserResponseDTO result = adminUserService.updateUser(1L, request);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("유저가 없으면 EntityNotFoundException을 던진다")
        void updateUser_throwsException_whenUserNotFound() {
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            UserUpdateRequestDTO request = new UserUpdateRequestDTO("홍길동", "newPw", false);

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
        }

        @Test
        @DisplayName("FULFILLED Request가 있으면 인프라 리소스를 삭제하고 상태를 DELETED로 변경한다")
        void deleteUser_withFulfilledRequests_deletesResources() {
            Request request = createFulfilledRequest("testuser", "ailab-testuser-pod");

            when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
            when(requestRepository.findAllByUser(mockUser)).thenReturn(List.of(request));

            adminUserService.deleteUser(1L);

            verify(podService).deletePod("ailab-testuser-pod");
            verify(ubuntuAccountService).deleteUbuntuAccount("testuser");
            assertThat(request.getStatus()).isEqualTo(Status.DELETED);
            assertThat(mockUser.getIsActive()).isFalse();
        }

        @Test
        @DisplayName("Pod 삭제 실패 시 PodCleanupFailedEvent를 발행하고 나머지는 계속 진행한다")
        void deleteUser_podDeleteFails_publishesEventAndContinues() {
            Request request = createFulfilledRequest("testuser", "ailab-testuser-pod");

            when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
            when(requestRepository.findAllByUser(mockUser)).thenReturn(List.of(request));
            doThrow(new RuntimeException("K8s connection refused"))
                    .when(podService).deletePod("ailab-testuser-pod");

            adminUserService.deleteUser(1L);

            verify(ubuntuAccountService).deleteUbuntuAccount("testuser");
            assertThat(request.getStatus()).isEqualTo(Status.DELETED);

            ArgumentCaptor<PodCleanupFailedEvent> captor = ArgumentCaptor.forClass(PodCleanupFailedEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());
            assertThat(captor.getValue().podName()).isEqualTo("ailab-testuser-pod");
            assertThat(captor.getValue().username()).isEqualTo("testuser");
        }

        @Test
        @DisplayName("존재하지 않는 유저를 삭제하려 하면 EntityNotFoundException을 던진다")
        void deleteUser_throwsException_whenUserNotFound() {
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> adminUserService.deleteUser(99L))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("deleteUbuntuAccount")
    class DeleteUbuntuAccount {

        @Test
        @DisplayName("FULFILLED 상태의 Request가 있으면 리소스를 삭제한다")
        void deleteUbuntuAccount_fulfilled_deletesResources() {
            Request request = createFulfilledRequest("testuser", "ailab-testuser-pod");

            when(requestRepository.findByUbuntuUsername("testuser")).thenReturn(Optional.of(request));

            adminUserService.deleteUbuntuAccount("testuser");

            verify(podService).deletePod("ailab-testuser-pod");
            verify(ubuntuAccountService).deleteUbuntuAccount("testuser");
            assertThat(request.getStatus()).isEqualTo(Status.DELETED);
        }

        @Test
        @DisplayName("존재하지 않는 username이면 EntityNotFoundException을 던진다")
        void deleteUbuntuAccount_notFound_throwsException() {
            when(requestRepository.findByUbuntuUsername("unknown")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> adminUserService.deleteUbuntuAccount("unknown"))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }
}
