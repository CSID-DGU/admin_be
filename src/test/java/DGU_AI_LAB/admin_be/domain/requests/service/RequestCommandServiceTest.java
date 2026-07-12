package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.domain.alarm.service.AlarmService;
import DGU_AI_LAB.admin_be.domain.containerImage.entity.ContainerImage;
import DGU_AI_LAB.admin_be.domain.containerImage.repository.ContainerImageRepository;
import DGU_AI_LAB.admin_be.domain.groups.repository.GroupRepository;
import DGU_AI_LAB.admin_be.domain.portRequests.service.PortRequestService;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.ModifyRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.SaveRequestRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.repository.ChangeRequestRepository;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup;
import DGU_AI_LAB.admin_be.domain.resourceGroups.repository.ResourceGroupRepository;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RequestCommandServiceTest {

    @InjectMocks
    private RequestCommandService requestCommandService;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private RequestRepository requestRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ContainerImageRepository containerImageRepository;

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private ResourceGroupRepository resourceGroupRepository;

    @Mock
    private ChangeRequestRepository changeRequestRepository;

    @Mock
    private PortRequestService portRequestService;

    @Mock
    private AlarmService alarmService;

    @Nested
    @DisplayName("createRequest")
    class CreateRequest {

        @Test
        @DisplayName("중복된 ubuntuUsername으로 요청을 생성하면 BusinessException을 던진다")
        void createRequest_throwsException_whenDuplicateUsername() {
            User user = User.builder()
                    .email("test@dgu.ac.kr").password("pw").name("홍길동")
                    .studentId("2021001234").phone("010-0000-0000").department("컴퓨터공학과")
                    .build();
            ResourceGroup rg = ResourceGroup.builder().resourceGroupName("GPU-A").serverName("server01").build();

            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(resourceGroupRepository.findById(any())).thenReturn(Optional.of(rg));
            when(requestRepository.existsByUbuntuUsernameAndStatusIn(eq("existinguser"), anyList())).thenReturn(true);

            SaveRequestRequestDTO dto = mock(SaveRequestRequestDTO.class);
            when(dto.resourceGroupId()).thenReturn(1);
            when(dto.ubuntuUsername()).thenReturn("existinguser");

            assertThatThrownBy(() -> requestCommandService.createRequest(1L, dto))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("유저가 없으면 BusinessException을 던진다")
        void createRequest_throwsException_whenUserNotFound() {
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            SaveRequestRequestDTO dto = mock(SaveRequestRequestDTO.class);

            assertThatThrownBy(() -> requestCommandService.createRequest(99L, dto))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("리소스 그룹이 없으면 BusinessException을 던진다")
        void createRequest_throwsException_whenResourceGroupNotFound() {
            User user = User.builder()
                    .email("test@dgu.ac.kr").password("pw").name("홍길동")
                    .studentId("2021001234").phone("010-0000-0000").department("컴퓨터공학과")
                    .build();

            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(resourceGroupRepository.findById(any())).thenReturn(Optional.empty());

            SaveRequestRequestDTO dto = mock(SaveRequestRequestDTO.class);
            when(dto.resourceGroupId()).thenReturn(1);

            assertThatThrownBy(() -> requestCommandService.createRequest(1L, dto))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    @DisplayName("createModificationRequest")
    class CreateModificationRequest {

        @Test
        @DisplayName("존재하지 않는 requestId로 변경 요청하면 BusinessException을 던진다")
        void createModificationRequest_throwsException_whenRequestNotFound() {
            when(requestRepository.findById(99L)).thenReturn(Optional.empty());

            ModifyRequestDTO dto = mock(ModifyRequestDTO.class);

            assertThatThrownBy(() -> requestCommandService.createModificationRequest(1L, 99L, dto))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("요청 소유자가 아닌 유저가 변경 요청하면 BusinessException을 던진다")
        void createModificationRequest_throwsException_whenNotOwner() {
            User owner = mock(User.class);
            when(owner.getUserId()).thenReturn(1L);

            Request request = mock(Request.class);
            when(request.getUser()).thenReturn(owner);
            when(requestRepository.findById(10L)).thenReturn(Optional.of(request));

            ModifyRequestDTO dto = mock(ModifyRequestDTO.class);

            // userId=2 로 요청 → 소유자 userId=1 과 불일치
            assertThatThrownBy(() -> requestCommandService.createModificationRequest(2L, 10L, dto))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("FULFILLED 상태가 아닌 요청에 변경 요청하면 BusinessException을 던진다")
        void createModificationRequest_throwsException_whenStatusIsNotFulfilled() {
            User owner = mock(User.class);
            when(owner.getUserId()).thenReturn(1L);

            Request request = mock(Request.class);
            when(request.getUser()).thenReturn(owner);
            when(request.getStatus()).thenReturn(Status.PENDING);
            when(requestRepository.findById(11L)).thenReturn(Optional.of(request));

            ModifyRequestDTO dto = mock(ModifyRequestDTO.class);

            assertThatThrownBy(() -> requestCommandService.createModificationRequest(1L, 11L, dto))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("소유자를 DB에서 찾을 수 없으면 BusinessException을 던진다")
        void createModificationRequest_throwsException_whenUserNotFound() {
            User owner = mock(User.class);
            when(owner.getUserId()).thenReturn(1L);

            Request request = mock(Request.class);
            when(request.getUser()).thenReturn(owner);
            when(request.getStatus()).thenReturn(Status.FULFILLED);
            when(requestRepository.findById(12L)).thenReturn(Optional.of(request));

            // dto stubs 불필요 - userRepository.findById 에서 이미 예외 발생
            ModifyRequestDTO dto = mock(ModifyRequestDTO.class);

            assertThatThrownBy(() -> requestCommandService.createModificationRequest(1L, 12L, dto))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    @DisplayName("createRequest 추가 시나리오")
    class CreateRequestAdditional {

        @Test
        @DisplayName("컨테이너 이미지가 없으면 BusinessException을 던진다")
        void createRequest_throwsException_whenContainerImageNotFound() {
            User user = User.builder()
                    .email("test@dgu.ac.kr").password("pw").name("홍길동")
                    .studentId("2021001234").phone("010-0000-0000").department("컴퓨터공학과")
                    .build();
            ResourceGroup rg = ResourceGroup.builder().resourceGroupName("GPU-A").serverName("server01").build();

            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(resourceGroupRepository.findById(any())).thenReturn(Optional.of(rg));
            when(requestRepository.existsByUbuntuUsernameAndStatusIn(any(), anyList())).thenReturn(false);
            when(containerImageRepository.findById(any())).thenReturn(Optional.empty());

            SaveRequestRequestDTO dto = mock(SaveRequestRequestDTO.class);
            when(dto.resourceGroupId()).thenReturn(1);
            when(dto.ubuntuUsername()).thenReturn("newuser");
            when(dto.imageId()).thenReturn(99L);

            assertThatThrownBy(() -> requestCommandService.createRequest(1L, dto))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("그룹 ID 중 존재하지 않는 GID가 있으면 BusinessException을 던진다")
        void createRequest_throwsException_whenGroupGidNotFound() {
            User user = User.builder()
                    .email("test@dgu.ac.kr").password("pw").name("홍길동")
                    .studentId("2021001234").phone("010-0000-0000").department("컴퓨터공학과")
                    .build();
            ResourceGroup rg = ResourceGroup.builder().resourceGroupName("GPU-A").serverName("server01").build();
            ContainerImage img = ContainerImage.builder()
                    .imageName("cuda").imageVersion("11.8").cudaVersion("11.8").description("test").build();

            Request savedReq = mock(Request.class);

            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(resourceGroupRepository.findById(any())).thenReturn(Optional.of(rg));
            when(requestRepository.existsByUbuntuUsernameAndStatusIn(any(), anyList())).thenReturn(false);
            when(containerImageRepository.findById(any())).thenReturn(Optional.of(img));

            SaveRequestRequestDTO dto = mock(SaveRequestRequestDTO.class);
            when(dto.resourceGroupId()).thenReturn(1);
            when(dto.ubuntuUsername()).thenReturn("newuser");
            when(dto.imageId()).thenReturn(1L);
            when(dto.ubuntuPassword()).thenReturn("password");
            when(dto.toEntity(any(), any(), any(), any(), any())).thenReturn(savedReq);
            when(requestRepository.save(any())).thenReturn(savedReq);
            // GID 2개 요청했지만 0개만 발견 → 예외 발생 (portRequests 도달 전)
            when(dto.ubuntuGids()).thenReturn(java.util.Set.of(1001L, 1002L));
            when(groupRepository.findAllByUbuntuGidIn(any())).thenReturn(List.of());

            assertThatThrownBy(() -> requestCommandService.createRequest(1L, dto))
                    .isInstanceOf(BusinessException.class);
        }
    }
}
