package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.domain.alarm.service.AlarmService;
import DGU_AI_LAB.admin_be.domain.containerImage.entity.ContainerImage;
import DGU_AI_LAB.admin_be.domain.containerImage.repository.ContainerImageRepository;
import DGU_AI_LAB.admin_be.domain.groups.entity.Group;
import DGU_AI_LAB.admin_be.domain.groups.repository.GroupRepository;
import DGU_AI_LAB.admin_be.domain.groups.service.PendingGroupService;
import DGU_AI_LAB.admin_be.domain.portRequests.service.PortRequestService;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.SingleChangeRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.SaveRequestRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.SaveRequestResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.entity.ChangeType;
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

import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Optional;
import java.util.Set;

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

    @Mock
    private RequestCreateThrottle requestCreateThrottle;

    @Mock
    private PendingGroupService pendingGroupService;

    /** 가입 시 우분투 계정명이 정해진 사용자 — 신청은 이 값을 그대로 복사해 쓴다. */
    /** 가입(또는 로그인) 때 웹 비밀번호로 SSH 비밀번호 해시가 채워진 사용자. */
    private static User userWithUbuntuUsername(String ubuntuUsername) {
        User user = userWithoutSshPassword(ubuntuUsername);
        user.changeUbuntuPasswordHash("$6$existing$hash");
        return user;
    }

    /** SSH 비밀번호 해시가 생기기 전에 로그인해 둔 세션의 사용자. */
    private static User userWithoutSshPassword(String ubuntuUsername) {
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
        @DisplayName("슬랙 알림은 트랜잭션 커밋 후에만 보낸다 — 사용자 행 잠금을 쥔 채 전송하지 않고, 롤백되면 보내지 않는다")
        void createRequest_sendsSlackOnlyAfterCommit() {
            User user = userWithUbuntuUsername("honggildong");
            ResourceGroup rg = ResourceGroup.builder().resourceGroupName("GPU-A").serverName("server01").build();
            ContainerImage img = ContainerImage.builder()
                    .imageName("cuda").imageVersion("11.8").cudaVersion("11.8").description("test").build();
            Request savedReq = Request.builder()
                    .expiresAt(LocalDateTime.now().plusDays(30)).usagePurpose("연구").formAnswers("{}")
                    .user(user).resourceGroup(rg).containerImage(img).build();
            when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
            when(resourceGroupRepository.findById(any())).thenReturn(Optional.of(rg));
            when(containerImageRepository.findById(any())).thenReturn(Optional.of(img));
            SaveRequestRequestDTO dto = mock(SaveRequestRequestDTO.class);
            when(dto.resourceGroupId()).thenReturn(1);
            when(dto.imageId()).thenReturn(1L);
            when(dto.toEntity(any(), any(), any())).thenReturn(savedReq);
            when(requestRepository.saveAndFlush(any())).thenReturn(savedReq);

            TransactionSynchronizationManager.initSynchronization();
            try {
                requestCommandService.createRequest(1L, dto);
                verifyNoInteractions(alarmService);

                TransactionSynchronizationManager.getSynchronizations()
                        .forEach(TransactionSynchronization::afterCommit);
                verify(alarmService).sendNewRequestNotification(eq(savedReq), any(), anyLong());
                verify(alarmService).sendRequestReceivedEmail(savedReq);
            } finally {
                TransactionSynchronizationManager.clearSynchronization();
            }
        }

        @Test
        @DisplayName("사용 중인 컨테이너가 있어도 새 신청을 막지 않는다 — 승인 대기·처리 중인 신청만 센다")
        void createRequest_succeeds_evenWhenUserAlreadyHasActiveContainer() {
            User user = userWithUbuntuUsername("honggildong");
            ResourceGroup rg = ResourceGroup.builder().resourceGroupName("GPU-A").serverName("server01").build();
            ContainerImage img = ContainerImage.builder()
                    .imageName("cuda").imageVersion("11.8").cudaVersion("11.8").description("test").build();

            Request savedReq = Request.builder()
                    .expiresAt(LocalDateTime.now().plusDays(30))
                    .usagePurpose("연구")
                    .formAnswers("{}")
                    .user(user)
                    .resourceGroup(rg)
                    .containerImage(img)
                    .build();

            when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
            when(resourceGroupRepository.findById(any())).thenReturn(Optional.of(rg));
            when(containerImageRepository.findById(any())).thenReturn(Optional.of(img));

            SaveRequestRequestDTO dto = mock(SaveRequestRequestDTO.class);
            when(dto.resourceGroupId()).thenReturn(1);
            when(dto.imageId()).thenReturn(1L);
            when(dto.toEntity(any(), any(), any())).thenReturn(savedReq);
            when(requestRepository.saveAndFlush(any())).thenReturn(savedReq);

            assertThat(requestCommandService.createRequest(1L, dto).ubuntuUsername())
                    .isEqualTo("honggildong");
            verify(requestRepository).existsByUser_UserIdAndStatusIn(1L, List.of(Status.PENDING, Status.PROCESSING));
        }

        @Test
        @DisplayName("승인 대기·처리 중인 신청이 있으면 저장하지 않고 409로 거절하며 하루 한도에 세지 않는다")
        void createRequest_rejected_whenUserHasRequestAwaitingDecision() {
            User user = userWithUbuntuUsername("honggildong");
            ResourceGroup rg = ResourceGroup.builder().resourceGroupName("GPU-A").serverName("server01").build();
            when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
            when(resourceGroupRepository.findById(any())).thenReturn(Optional.of(rg));
            when(requestRepository.existsByUser_UserIdAndStatusIn(1L, List.of(Status.PENDING, Status.PROCESSING)))
                    .thenReturn(true);
            SaveRequestRequestDTO dto = mock(SaveRequestRequestDTO.class);
            when(dto.resourceGroupId()).thenReturn(1);

            assertThatThrownBy(() -> requestCommandService.createRequest(1L, dto))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.CONTAINER_REQUEST_ALREADY_PENDING);
            verify(requestRepository, never()).saveAndFlush(any());
            verifyNoInteractions(requestCreateThrottle);
        }

        @Test
        @DisplayName("가입 시 정해진 우분투 계정명이 없으면 UBUNTU_USERNAME_NOT_ASSIGNED를 던진다")
        void createRequest_throwsException_whenUserHasNoUbuntuUsername() {
            User user = User.builder()
                    .email("test@dgu.ac.kr").password("pw").name("홍길동")
                    .studentId("2021001234").phone("010-0000-0000").department("컴퓨터공학과")
                    .build();
            ResourceGroup rg = ResourceGroup.builder().resourceGroupName("GPU-A").serverName("server01").build();

            when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
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
            when(userRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

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

            when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
            when(resourceGroupRepository.findById(any())).thenReturn(Optional.empty());

            SaveRequestRequestDTO dto = mock(SaveRequestRequestDTO.class);
            when(dto.resourceGroupId()).thenReturn(1);

            assertThatThrownBy(() -> requestCommandService.createRequest(1L, dto))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    @DisplayName("createSingleChangeRequest")
    class CreateSingleChangeRequest {

        @Test
        @DisplayName("존재하지 않는 requestId로 변경 요청하면 BusinessException을 던진다")
        void createSingleChangeRequest_throwsException_whenRequestNotFound() {
            when(requestRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

            SingleChangeRequestDTO dto = new SingleChangeRequestDTO(ChangeType.GROUP, "[1005]", "사유");

            assertThatThrownBy(() -> requestCommandService.createSingleChangeRequest(1L, 99L, dto))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("요청 소유자가 아닌 유저가 변경 요청하면 BusinessException을 던진다")
        void createSingleChangeRequest_throwsException_whenNotOwner() {
            User owner = mock(User.class);
            when(owner.getUserId()).thenReturn(1L);

            Request request = mock(Request.class);
            when(request.getUser()).thenReturn(owner);
            when(requestRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(request));

            SingleChangeRequestDTO dto = new SingleChangeRequestDTO(ChangeType.GROUP, "[1005]", "사유");

            // userId=2 로 요청 → 소유자 userId=1 과 불일치
            assertThatThrownBy(() -> requestCommandService.createSingleChangeRequest(2L, 10L, dto))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("FULFILLED 상태가 아닌 요청에 변경 요청하면 BusinessException을 던진다")
        void createSingleChangeRequest_throwsException_whenStatusIsNotFulfilled() {
            User owner = mock(User.class);
            when(owner.getUserId()).thenReturn(1L);

            Request request = mock(Request.class);
            when(request.getUser()).thenReturn(owner);
            when(request.getStatus()).thenReturn(Status.PENDING);
            when(requestRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(request));

            SingleChangeRequestDTO dto = new SingleChangeRequestDTO(ChangeType.GROUP, "[1005]", "사유");

            assertThatThrownBy(() -> requestCommandService.createSingleChangeRequest(1L, 11L, dto))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("같은 신청에 같은 종류의 변경 요청이 이미 대기 중이면 새로 받지 않는다")
        void createSingleChangeRequest_throwsConflict_whenSameTypeAlreadyPending() {
            User owner = mock(User.class);
            when(owner.getUserId()).thenReturn(1L);
            Request request = mock(Request.class);
            when(request.getUser()).thenReturn(owner);
            when(request.getStatus()).thenReturn(Status.FULFILLED);
            when(requestRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(request));
            when(changeRequestRepository.existsByRequest_RequestIdAndChangeTypeAndStatus(12L, ChangeType.GROUP, Status.PENDING))
                    .thenReturn(true);

            SingleChangeRequestDTO dto = new SingleChangeRequestDTO(ChangeType.GROUP, "[1005]", "사유");

            assertThatThrownBy(() -> requestCommandService.createSingleChangeRequest(1L, 12L, dto))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.CHANGE_REQUEST_ALREADY_PENDING);
            verify(changeRequestRepository, never()).save(any());
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

            when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
            when(resourceGroupRepository.findById(any())).thenReturn(Optional.of(rg));
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

            when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
            when(resourceGroupRepository.findById(any())).thenReturn(Optional.of(rg));
            when(containerImageRepository.findById(any())).thenReturn(Optional.of(img));

            SaveRequestRequestDTO dto = mock(SaveRequestRequestDTO.class);
            when(dto.resourceGroupId()).thenReturn(1);
            when(dto.imageId()).thenReturn(1L);
            // GID 2개 요청했지만 0개만 발견 → 예외 발생 (저장 전)
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

            when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
            when(resourceGroupRepository.findById(any())).thenReturn(Optional.of(rg));
            when(containerImageRepository.findById(any())).thenReturn(Optional.of(img));

            // 응답 DTO 조립까지 통과해야 하므로 mock 대신 실제 엔티티를 저장 결과로 돌려준다.
            Request savedReq = Request.builder()
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
            when(dto.toEntity(any(), any(), any())).thenReturn(savedReq);
            when(requestRepository.saveAndFlush(any())).thenReturn(savedReq);

            SaveRequestResponseDTO response = requestCommandService.createRequest(1L, dto);

            verify(dto).toEntity(eq(user), eq(rg), eq(img));
            assertThat(response.ubuntuUsername()).isEqualTo("honggildong");
        }

        private SaveRequestRequestDTO stubbedCreate(User user) {
            ResourceGroup rg = ResourceGroup.builder().resourceGroupName("GPU-A").serverName("server01").build();
            ContainerImage img = ContainerImage.builder()
                    .imageName("cuda").imageVersion("11.8").cudaVersion("11.8").description("test").build();
            lenient().when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
            lenient().when(resourceGroupRepository.findById(any())).thenReturn(Optional.of(rg));
            lenient().when(containerImageRepository.findById(any())).thenReturn(Optional.of(img));
            Request savedReq = Request.builder()
                    .expiresAt(LocalDateTime.now().plusDays(30))
                    .usagePurpose("연구").formAnswers("{}")
                    .user(user).resourceGroup(rg).containerImage(img)
                    .build();
            SaveRequestRequestDTO dto = mock(SaveRequestRequestDTO.class);
            lenient().when(dto.resourceGroupId()).thenReturn(1);
            lenient().when(dto.imageId()).thenReturn(1L);
            lenient().when(dto.toEntity(any(), any(), any())).thenReturn(savedReq);
            lenient().when(requestRepository.saveAndFlush(any())).thenReturn(savedReq);
            return dto;
        }

        @Test
        @DisplayName("신청은 비밀번호를 받지 않고 웹 계정의 SSH 비밀번호 해시를 그대로 둔다")
        void createRequest_keepsAccountPassword() {
            User user = userWithUbuntuUsername("honggildong");

            requestCommandService.createRequest(1L, stubbedCreate(user));

            assertThat(user.getUbuntuPasswordHash()).isEqualTo("$6$existing$hash");
        }

        @Test
        @DisplayName("SSH 비밀번호 해시가 없는 세션이면 저장하지 않고 UBUNTU_PASSWORD_REQUIRED(다시 로그인 안내)")
        void createRequest_requiresSshPasswordHash() {
            User user = userWithoutSshPassword("honggildong");
            SaveRequestRequestDTO dto = stubbedCreate(user);

            assertThatThrownBy(() -> requestCommandService.createRequest(1L, dto))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.UBUNTU_PASSWORD_REQUIRED);
            verify(requestRepository, never()).saveAndFlush(any());
            verifyNoInteractions(requestCreateThrottle);
        }

        @Test
        @DisplayName("없는 그룹을 고른 신청은 하루 한도에 세지 않는다")
        void createRequest_unknownGroup_doesNotCountTowardDailyLimit() {
            User user = userWithUbuntuUsername("honggildong");
            SaveRequestRequestDTO dto = stubbedCreate(user);
            when(dto.ubuntuGids()).thenReturn(Set.of(987654L));
            when(groupRepository.findAllByUbuntuGidIn(any())).thenReturn(List.of());

            assertThatThrownBy(() -> requestCommandService.createRequest(1L, dto))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
            verify(requestRepository, never()).saveAndFlush(any());
            verifyNoInteractions(requestCreateThrottle);
        }

        @Test
        @DisplayName("groupIds로 고른 그룹(승인 대기 그룹 포함)을 잠그며 찾아 신청에 담는다")
        void createRequest_byGroupIds_locksAndAttachesGroups() {
            User user = userWithUbuntuUsername("honggildong");
            SaveRequestRequestDTO dto = stubbedCreate(user);
            Request savedReq = dto.toEntity(null, null, null);
            ReflectionTestUtils.setField(savedReq, "requestId", 77L);
            Group pending = Group.builder().groupName("vision-lab").build();
            ReflectionTestUtils.setField(pending, "groupId", 3L);
            Group created = Group.builder().groupName("ailab").ubuntuGid(2001L).build();
            ReflectionTestUtils.setField(created, "groupId", 4L);
            when(dto.groupIds()).thenReturn(Set.of(3L, 4L));
            when(groupRepository.findAllByIdForUpdate(Set.of(3L, 4L))).thenReturn(List.of(pending, created));

            requestCommandService.createRequest(1L, dto);

            assertThat(savedReq.getRequestGroups()).extracting(rg -> rg.getGroup().getGroupName())
                    .containsExactlyInAnyOrder("vision-lab", "ailab");
            verify(groupRepository, never()).findAllByUbuntuGidIn(any());
        }

        @Test
        @DisplayName("groupIds 중 없는 그룹(예: 그 사이 지워진 승인 대기 그룹)이 있으면 저장하지 않고 한도도 세지 않는다")
        void createRequest_byGroupIds_unknownGroup_isRejected() {
            User user = userWithUbuntuUsername("honggildong");
            SaveRequestRequestDTO dto = stubbedCreate(user);
            when(dto.groupIds()).thenReturn(Set.of(3L, 4L));
            when(groupRepository.findAllByIdForUpdate(any())).thenReturn(List.of());

            assertThatThrownBy(() -> requestCommandService.createRequest(1L, dto))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
            verify(requestRepository, never()).saveAndFlush(any());
            verifyNoInteractions(requestCreateThrottle);
        }

        @Test
        @DisplayName("groupIds와 ubuntuGids를 함께 보내면 어느 쪽을 믿을지 알 수 없어 400으로 거절한다")
        void createRequest_bothGroupFields_isRejected() {
            User user = userWithUbuntuUsername("honggildong");
            SaveRequestRequestDTO dto = stubbedCreate(user);
            when(dto.groupIds()).thenReturn(Set.of(3L));
            when(dto.ubuntuGids()).thenReturn(Set.of(2001L));

            assertThatThrownBy(() -> requestCommandService.createRequest(1L, dto))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
            verify(requestRepository, never()).saveAndFlush(any());
            verifyNoInteractions(requestCreateThrottle);
        }

        @Test
        @DisplayName("하루 신청 한도를 넘으면 저장하지 않고 429로 거절한다")
        void createRequest_overDailyLimit_isRejected() {
            User user = userWithUbuntuUsername("honggildong");
            SaveRequestRequestDTO dto = stubbedCreate(user);
            doThrow(new BusinessException(ErrorCode.TOO_MANY_CONTAINER_REQUESTS)).when(requestCreateThrottle).acquire(1L);

            assertThatThrownBy(() -> requestCommandService.createRequest(1L, dto))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.TOO_MANY_CONTAINER_REQUESTS);
            verify(requestRepository, never()).saveAndFlush(any());
        }
    }

    @Nested
    @DisplayName("cancelRequest")
    class CancelRequest {

        private Request buildRequest() {
            User owner = mock(User.class);
            when(owner.getUserId()).thenReturn(1L);

            return Request.builder()
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
            verifyNoInteractions(pendingGroupService);
        }

        @Test
        @DisplayName("승인을 기다리던 신청을 취소하면 신청서 채널 알림을 커밋 뒤에 보낸다")
        void cancelRequest_notifiesRequestChannelAfterCommit_whenPending() {
            Request request = buildRequest();
            when(requestRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(request));
            Runnable send = mock(Runnable.class);
            when(alarmService.prepareRequestCancelledNotification(request)).thenReturn(send);

            TransactionSynchronizationManager.initSynchronization();
            try {
                requestCommandService.cancelRequest(1L, 11L);
                verify(alarmService).prepareRequestCancelledNotification(request);
                verifyNoInteractions(send);

                TransactionSynchronizationManager.getSynchronizations()
                        .forEach(TransactionSynchronization::afterCommit);
                verify(send).run();
            } finally {
                TransactionSynchronizationManager.clearSynchronization();
            }
        }

        @Test
        @DisplayName("거절된 신청을 지울 때는 신청서 채널에 알리지 않는다 — 이미 처리가 끝난 신청이다")
        void cancelRequest_doesNotNotify_whenDenied() {
            Request request = buildRequest();
            request.reject("사유");
            when(requestRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(request));

            requestCommandService.cancelRequest(1L, 12L);

            verifyNoInteractions(alarmService);
        }

        @Test
        @DisplayName("취소 알림을 만들지 못해도 취소는 끝난다")
        void cancelRequest_succeeds_whenNoticeFails() {
            Request request = buildRequest();
            when(requestRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(request));
            when(alarmService.prepareRequestCancelledNotification(request)).thenThrow(new IllegalStateException("boom"));

            requestCommandService.cancelRequest(1L, 11L);

            assertThat(request.getStatus()).isEqualTo(Status.DELETED);
        }

        @Test
        @DisplayName("PENDING 상태의 본인 신청을 취소하면 DELETED로 바뀐다")
        void cancelRequest_succeeds_whenPending() {
            Request request = buildRequest();
            when(requestRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(request));

            requestCommandService.cancelRequest(1L, 11L);

            assertThat(request.getStatus()).isEqualTo(Status.DELETED);
            // 이 신청이 승인 대기 그룹을 고른 마지막 신청이면 그 그룹을 지운다.
            verify(pendingGroupService).deleteAbandoned(request);
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
            request.markAsProcessing();
            request.prepareAsyncApproval(mock(ContainerImage.class), mock(ResourceGroup.class), null);
            request.completeApproval();
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
            request.markAsProcessing();
            request.prepareAsyncApproval(mock(ContainerImage.class), mock(ResourceGroup.class), null);
            request.completeApproval();
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
