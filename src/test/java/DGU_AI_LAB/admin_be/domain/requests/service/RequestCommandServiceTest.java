package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.domain.alarm.service.AlarmService;
import DGU_AI_LAB.admin_be.domain.containerImage.entity.ContainerImage;
import DGU_AI_LAB.admin_be.domain.containerImage.repository.ContainerImageRepository;
import DGU_AI_LAB.admin_be.domain.groups.repository.GroupRepository;
import DGU_AI_LAB.admin_be.domain.portRequests.service.PortRequestService;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.ModifyRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.SaveRequestRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.SaveRequestResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.repository.ChangeRequestRepository;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup;
import DGU_AI_LAB.admin_be.domain.resourceGroups.repository.ResourceGroupRepository;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;

import java.time.LocalDateTime;
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

import static org.assertj.core.api.Assertions.assertThat;
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

    /** 가입 시 우분투 계정명이 정해진 사용자 — 신청은 이 값을 그대로 복사해 쓴다. */
    private static User userWithUbuntuUsername(String ubuntuUsername) {
        return User.builder()
                .email("test@dgu.ac.kr").password("pw").name("홍길동")
                .studentId("2021001234").phone("010-0000-0000").department("컴퓨터공학과")
                .ubuntuUsername(ubuntuUsername)
                .build();
    }

    @Nested
    @DisplayName("createRequest")
    class CreateRequest {

        @Test
        @DisplayName("이미 살아있는 신청이 있으면 ACTIVE_REQUEST_ALREADY_EXISTS를 던진다 — 유저네임이 같아 인프라 API가 두 컨테이너를 구분할 수 없다")
        void createRequest_throwsException_whenUserAlreadyHasOpenRequest() {
            User user = userWithUbuntuUsername("honggildong");
            ResourceGroup rg = ResourceGroup.builder().resourceGroupName("GPU-A").serverName("server01").build();

            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(resourceGroupRepository.findById(any())).thenReturn(Optional.of(rg));
            when(requestRepository.existsByUser_UserIdAndStatusIn(1L, Status.openStatuses())).thenReturn(true);

            SaveRequestRequestDTO dto = mock(SaveRequestRequestDTO.class);
            when(dto.resourceGroupId()).thenReturn(1);

            assertThatThrownBy(() -> requestCommandService.createRequest(1L, dto))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACTIVE_REQUEST_ALREADY_EXISTS);
        }

        @Test
        @DisplayName("가입 시 정해진 우분투 계정명이 없으면 UBUNTU_USERNAME_NOT_ASSIGNED를 던진다")
        void createRequest_throwsException_whenUserHasNoUbuntuUsername() {
            User user = User.builder()
                    .email("test@dgu.ac.kr").password("pw").name("홍길동")
                    .studentId("2021001234").phone("010-0000-0000").department("컴퓨터공학과")
                    .build();
            ResourceGroup rg = ResourceGroup.builder().resourceGroupName("GPU-A").serverName("server01").build();

            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(resourceGroupRepository.findById(any())).thenReturn(Optional.of(rg));

            SaveRequestRequestDTO dto = mock(SaveRequestRequestDTO.class);
            when(dto.resourceGroupId()).thenReturn(1);

            assertThatThrownBy(() -> requestCommandService.createRequest(1L, dto))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UBUNTU_USERNAME_NOT_ASSIGNED);
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
            User user = userWithUbuntuUsername("newuser");
            ResourceGroup rg = ResourceGroup.builder().resourceGroupName("GPU-A").serverName("server01").build();

            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(resourceGroupRepository.findById(any())).thenReturn(Optional.of(rg));
            when(requestRepository.existsByUser_UserIdAndStatusIn(any(), anyList())).thenReturn(false);
            when(containerImageRepository.findById(any())).thenReturn(Optional.empty());

            SaveRequestRequestDTO dto = mock(SaveRequestRequestDTO.class);
            when(dto.resourceGroupId()).thenReturn(1);
            when(dto.imageId()).thenReturn(99L);

            assertThatThrownBy(() -> requestCommandService.createRequest(1L, dto))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("그룹 ID 중 존재하지 않는 GID가 있으면 BusinessException을 던진다")
        void createRequest_throwsException_whenGroupGidNotFound() {
            User user = userWithUbuntuUsername("newuser");
            ResourceGroup rg = ResourceGroup.builder().resourceGroupName("GPU-A").serverName("server01").build();
            ContainerImage img = ContainerImage.builder()
                    .imageName("cuda").imageVersion("11.8").cudaVersion("11.8").description("test").build();

            Request savedReq = mock(Request.class);

            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(resourceGroupRepository.findById(any())).thenReturn(Optional.of(rg));
            when(requestRepository.existsByUser_UserIdAndStatusIn(any(), anyList())).thenReturn(false);
            when(containerImageRepository.findById(any())).thenReturn(Optional.of(img));

            SaveRequestRequestDTO dto = mock(SaveRequestRequestDTO.class);
            when(dto.resourceGroupId()).thenReturn(1);
            when(dto.imageId()).thenReturn(1L);
            when(dto.toEntity(any(), any(), any(), anyString())).thenReturn(savedReq);
            when(requestRepository.saveAndFlush(any())).thenReturn(savedReq);
            // GID 2개 요청했지만 0개만 발견 → 예외 발생 (portRequests 도달 전)
            when(dto.ubuntuGids()).thenReturn(java.util.Set.of(1001L, 1002L));
            when(groupRepository.findAllByUbuntuGidIn(any())).thenReturn(List.of());

            assertThatThrownBy(() -> requestCommandService.createRequest(1L, dto))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("Request의 ubuntuUsername은 신청 입력이 아니라 가입 시 정해진 User.ubuntuUsername에서 가져온다")
        void createRequest_derivesUbuntuUsernameFromUser() {
            User user = userWithUbuntuUsername("honggildong");
            ResourceGroup rg = ResourceGroup.builder().resourceGroupName("GPU-A").serverName("server01").build();
            ContainerImage img = ContainerImage.builder()
                    .imageName("cuda").imageVersion("11.8").cudaVersion("11.8").description("test").build();

            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(resourceGroupRepository.findById(any())).thenReturn(Optional.of(rg));
            when(requestRepository.existsByUser_UserIdAndStatusIn(any(), anyList())).thenReturn(false);
            when(containerImageRepository.findById(any())).thenReturn(Optional.of(img));

            // 응답 DTO 조립까지 통과해야 하므로 mock 대신 실제 엔티티를 저장 결과로 돌려준다.
            Request savedReq = Request.builder()
                    .ubuntuUsername("honggildong")
                    .ubuntuPassword("pw")
                    .expiresAt(LocalDateTime.now().plusDays(30))
                    .usagePurpose("연구")
                    .formAnswers("{}")
                    .user(user)
                    .resourceGroup(rg)
                    .containerImage(img)
                    .build();
            SaveRequestRequestDTO dto = mock(SaveRequestRequestDTO.class);
            when(dto.resourceGroupId()).thenReturn(1);
            when(dto.imageId()).thenReturn(1L);
            when(dto.toEntity(any(), any(), any(), anyString())).thenReturn(savedReq);
            when(requestRepository.saveAndFlush(any())).thenReturn(savedReq);

            SaveRequestResponseDTO response = requestCommandService.createRequest(1L, dto);

            verify(dto).toEntity(eq(user), eq(rg), eq(img), eq("honggildong"));
            assertThat(response.ubuntuUsername()).isEqualTo("honggildong");
        }
    }

    @Nested
    @DisplayName("cancelRequest")
    class CancelRequest {

        private Request buildRequest() {
            User owner = mock(User.class);
            when(owner.getUserId()).thenReturn(1L);

            return Request.builder()
                    .ubuntuUsername("cancelUser")
                    .ubuntuPassword("hashedPassword")
                    .expiresAt(LocalDateTime.now().plusDays(30))
                    .usagePurpose("딥러닝 연구")
                    .formAnswers("{}")
                    .user(owner)
                    .resourceGroup(mock(ResourceGroup.class))
                    .containerImage(mock(ContainerImage.class))
                    .build();
        }

        @Test
        @DisplayName("존재하지 않는 requestId면 BusinessException을 던진다")
        void cancelRequest_throwsException_whenRequestNotFound() {
            when(requestRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> requestCommandService.cancelRequest(1L, 99L))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("본인 소유의 신청이 아니면 BusinessException을 던진다")
        void cancelRequest_throwsException_whenNotOwner() {
            Request request = buildRequest();
            when(requestRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(request));

            // userId=2 로 취소 시도 → 소유자 userId=1 과 불일치
            assertThatThrownBy(() -> requestCommandService.cancelRequest(2L, 10L))
                    .isInstanceOf(BusinessException.class);
            assertThat(request.getStatus()).isEqualTo(Status.PENDING);
        }

        @Test
        @DisplayName("PENDING 상태의 본인 신청을 취소하면 DELETED로 바뀐다")
        void cancelRequest_succeeds_whenPending() {
            Request request = buildRequest();
            when(requestRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(request));

            requestCommandService.cancelRequest(1L, 11L);

            assertThat(request.getStatus()).isEqualTo(Status.DELETED);
        }

        @Test
        @DisplayName("DENIED 상태의 본인 신청을 취소하면 DELETED로 바뀐다")
        void cancelRequest_succeeds_whenDenied() {
            Request request = buildRequest();
            request.reject("리소스 부족");
            when(requestRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(request));

            requestCommandService.cancelRequest(1L, 12L);

            assertThat(request.getStatus()).isEqualTo(Status.DELETED);
        }

        @Test
        @DisplayName("FULFILLED 상태의 신청을 취소하려 하면 BusinessException을 던지고 상태를 바꾸지 않는다")
        void cancelRequest_throwsException_whenFulfilled() {
            Request request = buildRequest();
            request.approve(mock(ContainerImage.class), mock(ResourceGroup.class), null);
            when(requestRepository.findByIdForUpdate(13L)).thenReturn(Optional.of(request));

            assertThatThrownBy(() -> requestCommandService.cancelRequest(1L, 13L))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_REQUEST_STATUS);
            assertThat(request.getStatus()).isEqualTo(Status.FULFILLED);
        }

        @Test
        @DisplayName("MIGRATING 상태의 신청을 취소하려 하면 BusinessException을 던지고 상태를 바꾸지 않는다")
        void cancelRequest_throwsException_whenMigrating() {
            Request request = buildRequest();
            request.approve(mock(ContainerImage.class), mock(ResourceGroup.class), null);
            request.beginMigration();
            when(requestRepository.findByIdForUpdate(14L)).thenReturn(Optional.of(request));

            assertThatThrownBy(() -> requestCommandService.cancelRequest(1L, 14L))
                    .isInstanceOf(BusinessException.class);
            assertThat(request.getStatus()).isEqualTo(Status.MIGRATING);
        }

        @Test
        @DisplayName("PROCESSING 상태(승인 처리 중)의 신청을 취소하려 하면 BusinessException을 던지고 상태를 바꾸지 않는다 — 안 막으면 처리 완료 후 DB에 추적 안 되는 고아 계정/Pod가 남는다")
        void cancelRequest_throwsException_whenProcessing() {
            Request request = buildRequest();
            request.markAsProcessing();
            when(requestRepository.findByIdForUpdate(15L)).thenReturn(Optional.of(request));

            assertThatThrownBy(() -> requestCommandService.cancelRequest(1L, 15L))
                    .isInstanceOf(BusinessException.class);
            assertThat(request.getStatus()).isEqualTo(Status.PROCESSING);
        }

        @Test
        @DisplayName("이미 삭제된 신청을 다시 취소하려 하면 BusinessException을 던진다")
        void cancelRequest_throwsException_whenAlreadyDeleted() {
            Request request = buildRequest();
            request.delete();
            when(requestRepository.findByIdForUpdate(16L)).thenReturn(Optional.of(request));

            assertThatThrownBy(() -> requestCommandService.cancelRequest(1L, 16L))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("취소 대상은 행 잠금(findByIdForUpdate)으로 조회한다 — 잠그지 않으면 동시 승인이 커밋한 PROCESSING을 DELETED로 덮어쓴다")
        void cancelRequest_locksRowForUpdate() {
            Request request = buildRequest();
            when(requestRepository.findByIdForUpdate(17L)).thenReturn(Optional.of(request));

            requestCommandService.cancelRequest(1L, 17L);

            verify(requestRepository).findByIdForUpdate(17L);
            verify(requestRepository, never()).findById(17L);
        }
    }
}
