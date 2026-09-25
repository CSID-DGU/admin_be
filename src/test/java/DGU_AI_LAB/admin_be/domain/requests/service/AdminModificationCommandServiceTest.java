package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.domain.alarm.service.AlarmService;
import DGU_AI_LAB.admin_be.domain.containerImage.entity.ContainerImage;
import DGU_AI_LAB.admin_be.domain.containerImage.repository.ContainerImageRepository;
import DGU_AI_LAB.admin_be.domain.groups.entity.Group;
import DGU_AI_LAB.admin_be.domain.groups.repository.GroupRepository;
import DGU_AI_LAB.admin_be.domain.groups.service.GroupService;
import DGU_AI_LAB.admin_be.domain.pod.entity.PodExternalPort;
import DGU_AI_LAB.admin_be.domain.pod.repository.PodExternalPortRepository;
import DGU_AI_LAB.admin_be.domain.portRequests.service.PortRequestService;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.ApproveModificationDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.ApproveRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.RejectModificationDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.RejectRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.UserCreationRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.CreatePodResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.SaveRequestResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.entity.ChangeRequest;
import DGU_AI_LAB.admin_be.domain.requests.entity.ChangeType;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.RequestGroup;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.repository.ChangeRequestRepository;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup;
import DGU_AI_LAB.admin_be.domain.resourceGroups.repository.ResourceGroupRepository;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import DGU_AI_LAB.admin_be.domain.requests.dto.request.ProvisionRegisterRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.JobResultResponseDTO;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminModificationCommandServiceTest {

    @Mock private AlarmService alarmService;
    @Mock private RequestRepository requestRepository;
    @Mock private UserRepository userRepository;
    @Mock private ContainerImageRepository containerImageRepository;
    @Mock private ResourceGroupRepository resourceGroupRepository;
    @Mock private ChangeRequestRepository changeRequestRepository;
    @Mock private GroupRepository groupRepository;
    @Mock private GroupService groupService;
    @Mock private PodExternalPortRepository podExternalPortRepository;
    @Mock private OperationJobService operationJobService;
    @Mock private PortRequestService portRequestService;
    @Mock private PlatformTransactionManager transactionManager;
    @Mock private TransactionStatus transactionStatus;


    // 공유 엔티티 mock - when() 내부에서 다른 mock 호출로 인한 UnfinishedStubbingException 방지
    @Mock private ContainerImage mockImage;
    @Mock private ResourceGroup mockRg;
    @Mock private User mockUser;

    private AdminModificationCommandService service;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);

        // @RequiredArgsConstructor 생성자 필드 선언 순서대로 주입
        service = new AdminModificationCommandService(
                alarmService, requestRepository, userRepository, containerImageRepository,
                resourceGroupRepository, changeRequestRepository,
                groupRepository, groupService, portRequestService, new ObjectMapper(),
                transactionManager
        );
        // 공유 엔티티 기본 설정
        when(mockUser.getName()).thenReturn("테스트유저");
        when(mockUser.getUserId()).thenReturn(100L);
        // 기본값: 아직 리눅스 계정이 없는 사용자 → 승인 시 계정 생성 API를 호출한다.
        when(mockUser.hasUbuntuAccount()).thenReturn(false);
        // UID/GID 배정은 User 행을 잠그고 수행한다 (같은 사용자의 동시 승인 직렬화).
        when(userRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(mockUser));
        when(mockImage.getImageId()).thenReturn(1L);
        when(mockRg.getRsgroupId()).thenReturn(1);
    }

    /** 공통 Request mock 설정 */
    // ──────────────────────────────────────────────────────────────────────
    // rejectModification 테스트
    // ──────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("rejectModification")
    class RejectModification {

        @Test
        @DisplayName("PENDING 상태 변경 요청을 거절하면 changeRequest.deny()가 호출된다")
        void rejectModification_success_whenStatusIsPending() {
            ChangeRequest changeRequest = mock(ChangeRequest.class);
            when(changeRequest.getStatus()).thenReturn(Status.PENDING);
            when(changeRequestRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(changeRequest));
            when(userRepository.findById(100L)).thenReturn(Optional.of(mockUser));

            RejectModificationDTO dto = new RejectModificationDTO(1L, "변경 사유 불충분");
            service.rejectModification(100L, dto);

            verify(changeRequest).deny(mockUser, "변경 사유 불충분");
        }

        @Test
        @DisplayName("존재하지 않는 변경 요청 ID로 거절하면 BusinessException을 던진다")
        void rejectModification_throwsException_whenChangeRequestNotFound() {
            when(changeRequestRepository.findByIdForUpdate(999L)).thenReturn(Optional.empty());

            RejectModificationDTO dto = new RejectModificationDTO(999L, "사유");

            assertThatThrownBy(() -> service.rejectModification(100L, dto))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("PENDING 이 아닌 상태의 변경 요청 거절 시 BusinessException을 던진다")
        void rejectModification_throwsException_whenStatusIsNotPending() {
            ChangeRequest changeRequest = mock(ChangeRequest.class);
            when(changeRequest.getStatus()).thenReturn(Status.FULFILLED);
            when(changeRequestRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(changeRequest));

            RejectModificationDTO dto = new RejectModificationDTO(2L, "사유");

            assertThatThrownBy(() -> service.rejectModification(100L, dto))
                    .isInstanceOf(BusinessException.class);
            verify(changeRequest, never()).deny(any(), anyString());
        }

        @Test
        @DisplayName("존재하지 않는 adminId로 거절하면 BusinessException을 던진다")
        void rejectModification_throwsException_whenAdminNotFound() {
            ChangeRequest changeRequest = mock(ChangeRequest.class);
            when(changeRequest.getStatus()).thenReturn(Status.PENDING);
            when(changeRequestRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(changeRequest));
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            RejectModificationDTO dto = new RejectModificationDTO(3L, "사유");

            assertThatThrownBy(() -> service.rejectModification(999L, dto))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("변경 요청 거절 성공 시 alarmService.sendModificationRejectedEmail이 호출된다")
        void rejectModification_sendsRejectionEmail_onSuccess() {
            ChangeRequest changeRequest = mock(ChangeRequest.class);
            when(changeRequest.getStatus()).thenReturn(Status.PENDING);
            when(changeRequestRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(changeRequest));
            when(userRepository.findById(100L)).thenReturn(Optional.of(mockUser));

            RejectModificationDTO dto = new RejectModificationDTO(10L, "변경 사유 불충분");
            service.rejectModification(100L, dto);

            verify(alarmService).sendModificationRejectedEmail(changeRequest, "변경 사유 불충분");
        }

        @Test
        @DisplayName("거절 사유가 이메일 발송 메서드에 그대로 전달된다")
        void rejectModification_passesAdminCommentToEmail() {
            ChangeRequest changeRequest = mock(ChangeRequest.class);
            when(changeRequest.getStatus()).thenReturn(Status.PENDING);
            when(changeRequestRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(changeRequest));
            when(userRepository.findById(100L)).thenReturn(Optional.of(mockUser));
            String comment = "리소스 여유 없음";

            service.rejectModification(100L, new RejectModificationDTO(11L, comment));

            verify(alarmService).sendModificationRejectedEmail(changeRequest, comment);
        }

        @Test
        @DisplayName("PENDING이 아닌 상태에서 거절 시 이메일이 발송되지 않는다")
        void rejectModification_doesNotSendEmail_whenStatusIsNotPending() {
            ChangeRequest changeRequest = mock(ChangeRequest.class);
            when(changeRequest.getStatus()).thenReturn(Status.FULFILLED);
            when(changeRequestRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(changeRequest));

            assertThatThrownBy(() -> service.rejectModification(100L, new RejectModificationDTO(12L, "사유")))
                    .isInstanceOf(BusinessException.class);

            verify(alarmService, never()).sendModificationRejectedEmail(any(), anyString());
        }

        @Test
        @DisplayName("이메일 발송 실패 시 예외가 전파되지 않고 deny()는 이미 호출된 상태다")
        void rejectModification_emailFailure_doesNotPropagateException() {
            ChangeRequest changeRequest = mock(ChangeRequest.class);
            when(changeRequest.getStatus()).thenReturn(Status.PENDING);
            when(changeRequestRepository.findByIdForUpdate(13L)).thenReturn(Optional.of(changeRequest));
            when(userRepository.findById(100L)).thenReturn(Optional.of(mockUser));
            doThrow(new RuntimeException("SMTP 연결 실패"))
                    .when(alarmService).sendModificationRejectedEmail(any(), anyString());

            // 이메일 실패해도 예외 미전파 (DB 롤백 없음)
            service.rejectModification(100L, new RejectModificationDTO(13L, "변경 사유 불충분"));

            verify(changeRequest).deny(mockUser, "변경 사유 불충분");
        }

        @Test
        @DisplayName("이메일 발송 시 RuntimeException 발생해도 deny()는 호출되고 정상 종료된다")
        void rejectModification_emailThrowsRuntimeException_denyStillCalled() {
            ChangeRequest changeRequest = mock(ChangeRequest.class);
            when(changeRequest.getStatus()).thenReturn(Status.PENDING);
            when(changeRequestRepository.findByIdForUpdate(14L)).thenReturn(Optional.of(changeRequest));
            when(userRepository.findById(100L)).thenReturn(Optional.of(mockUser));
            doThrow(new IllegalStateException("MessageUtils 키 없음"))
                    .when(alarmService).sendModificationRejectedEmail(any(), anyString());

            service.rejectModification(100L, new RejectModificationDTO(14L, "사유"));

            verify(changeRequest).deny(eq(mockUser), eq("사유"));
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // approveModification 테스트
    // ──────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("approveModification")
    class ApproveModification {

        @Test
        @DisplayName("EXPIRES_AT 변경 요청 승인 시 이전 만료일 캡처 후 updateExpiresAt()가 호출된다")
        void approveModification_expiresAt_success() throws Exception {
            LocalDateTime oldExpiry = LocalDateTime.of(2026, 6, 30, 23, 59, 59);
            LocalDateTime newExpiry = LocalDateTime.of(2027, 12, 31, 23, 59, 59);

            ChangeRequest changeRequest = mock(ChangeRequest.class);
            Request originalRequest = buildMockedRequestWithStatus(21L, Status.FULFILLED);
            when(originalRequest.getExpiresAt()).thenReturn(oldExpiry);
            when(changeRequest.getStatus()).thenReturn(Status.PENDING);
            when(changeRequest.getChangeType()).thenReturn(ChangeType.EXPIRES_AT);
            when(changeRequest.getNewValue()).thenReturn("\"2027-12-31T23:59:59\"");
            when(changeRequest.getRequest()).thenReturn(originalRequest);
            when(changeRequestRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(changeRequest));
            when(userRepository.findById(100L)).thenReturn(Optional.of(mockUser));

            ApproveModificationDTO dto = new ApproveModificationDTO(2L, "기간 연장 승인");
            service.approveModification(100L, dto);

            ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
            verify(originalRequest).updateExpiresAt(captor.capture());
            assertThat(captor.getValue()).isEqualTo(newExpiry);
            verify(changeRequest).approve(mockUser, "기간 연장 승인");
            verify(alarmService).sendContainerExtendedEmail(eq(originalRequest), eq(oldExpiry), eq(newExpiry));
            // EXPIRES_AT은 전용 메일(sendContainerExtendedEmail)만 보내고, 공용 승인 메일은 중복 발송하지 않는다
            verify(alarmService, never()).sendModificationApprovedEmail(any(), any());
        }

        @Test
        @DisplayName("RESOURCE_GROUP 변경 요청 승인 시 originalRequest.updateResourceGroup()이 호출된다")
        void approveModification_resourceGroup_success() throws Exception {
            ChangeRequest changeRequest = mock(ChangeRequest.class);
            Request originalRequest = buildMockedRequestWithStatus(22L, Status.FULFILLED);
            ResourceGroup newRg = mock(ResourceGroup.class);
            when(changeRequest.getStatus()).thenReturn(Status.PENDING);
            when(changeRequest.getChangeType()).thenReturn(ChangeType.RESOURCE_GROUP);
            when(changeRequest.getNewValue()).thenReturn("2");
            when(changeRequest.getRequest()).thenReturn(originalRequest);
            when(changeRequestRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(changeRequest));
            when(userRepository.findById(100L)).thenReturn(Optional.of(mockUser));
            when(resourceGroupRepository.findById(2)).thenReturn(Optional.of(newRg));

            ApproveModificationDTO dto = new ApproveModificationDTO(3L, "리소스 그룹 변경 승인");
            service.approveModification(100L, dto);

            verify(originalRequest).updateResourceGroup(newRg);
            verify(changeRequest).approve(mockUser, "리소스 그룹 변경 승인");
            verify(alarmService).sendModificationApprovedEmail(changeRequest, "리소스 그룹 변경 승인");
        }

        @Test
        @DisplayName("CONTAINER_IMAGE 변경 요청 승인 시 originalRequest.updateContainerImage()가 호출된다")
        void approveModification_containerImage_success() throws Exception {
            ChangeRequest changeRequest = mock(ChangeRequest.class);
            Request originalRequest = buildMockedRequestWithStatus(23L, Status.FULFILLED);
            ContainerImage newImage = mock(ContainerImage.class);
            when(changeRequest.getStatus()).thenReturn(Status.PENDING);
            when(changeRequest.getChangeType()).thenReturn(ChangeType.CONTAINER_IMAGE);
            when(changeRequest.getNewValue()).thenReturn("5");
            when(changeRequest.getRequest()).thenReturn(originalRequest);
            when(changeRequestRepository.findByIdForUpdate(4L)).thenReturn(Optional.of(changeRequest));
            when(userRepository.findById(100L)).thenReturn(Optional.of(mockUser));
            when(containerImageRepository.findById(5L)).thenReturn(Optional.of(newImage));

            ApproveModificationDTO dto = new ApproveModificationDTO(4L, "이미지 변경 승인");
            service.approveModification(100L, dto);

            verify(originalRequest).updateContainerImage(newImage);
            verify(changeRequest).approve(mockUser, "이미지 변경 승인");
            verify(alarmService).sendModificationApprovedEmail(changeRequest, "이미지 변경 승인");
        }

        @Test
        @DisplayName("GROUP 변경 요청 승인 시 originalRequest가 아니라 그 소유 계정(User)에 addGroupIfAbsent()가 호출된다")
        void approveModification_group_success() throws Exception {
            ChangeRequest changeRequest = mock(ChangeRequest.class);
            Request originalRequest = buildMockedRequestWithStatus(25L, Status.FULFILLED);
            Group newGroup = mock(Group.class);
            when(newGroup.getUbuntuGid()).thenReturn(42L);
            when(changeRequest.getStatus()).thenReturn(Status.PENDING);
            when(changeRequest.getChangeType()).thenReturn(ChangeType.GROUP);
            when(changeRequest.getNewValue()).thenReturn("[42]");
            when(changeRequest.getRequest()).thenReturn(originalRequest);
            when(changeRequestRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(changeRequest));
            when(userRepository.findById(100L)).thenReturn(Optional.of(mockUser));
            when(groupRepository.findByUbuntuGid(42L)).thenReturn(Optional.of(newGroup));

            ApproveModificationDTO dto = new ApproveModificationDTO(9L, "그룹 변경 승인");
            service.approveModification(100L, dto);

            // 계정 단위로 누적된다 — originalRequest.addGroup()은 더 이상 이 경로에서 안 쓰인다.
            verify(originalRequest, never()).addGroup(any());
            verify(mockUser).addGroupIfAbsent(newGroup);
            verify(changeRequest).approve(mockUser, "그룹 변경 승인");
            // 그룹 승인은 팀 디렉터리 경로를 담은 전용 안내로 보낸다.
            verify(alarmService).sendGroupAddedEmail(eq(changeRequest), eq("그룹 변경 승인"), anyList());
            verify(alarmService, never()).sendModificationApprovedEmail(any(), any());
            // AD 반영 직후 NAS GSS 온디맨드 flush를 트리거한다(admin_infra-proposed#161).
            verify(groupService).triggerNasGssFlush("testuser");
        }

        @Test
        @DisplayName("GROUP 변경 승인 중 addUserToGroups가 실패하면 DB에 반영되지 않고 예외가 그대로 전파된다(admin_be#554)")
        void approveModification_group_externalCallFails_doesNotMutateDb() throws Exception {
            ChangeRequest changeRequest = mock(ChangeRequest.class);
            Request originalRequest = buildMockedRequestWithStatus(27L, Status.FULFILLED);
            Group newGroup = mock(Group.class);
            when(newGroup.getUbuntuGid()).thenReturn(43L);
            when(changeRequest.getStatus()).thenReturn(Status.PENDING);
            when(changeRequest.getChangeType()).thenReturn(ChangeType.GROUP);
            when(changeRequest.getNewValue()).thenReturn("[43]");
            when(changeRequest.getRequest()).thenReturn(originalRequest);
            when(changeRequestRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(changeRequest));
            when(userRepository.findById(100L)).thenReturn(Optional.of(mockUser));
            when(groupRepository.findByUbuntuGid(43L)).thenReturn(Optional.of(newGroup));
            doThrow(new BusinessException(ErrorCode.AD_GROUP_SYNC_FAILED))
                    .when(groupService).addUserToGroups(anyString(), any());

            ApproveModificationDTO dto = new ApproveModificationDTO(10L, "그룹 변경 승인");

            assertThatThrownBy(() -> service.approveModification(100L, dto))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.AD_GROUP_SYNC_FAILED);

            // 외부 호출이 실패한 시점엔 1단계 트랜잭션이 이미 커밋 없이 끝난 뒤라 DB엔 아무 변경도 없어야 한다.
            verify(mockUser, never()).addGroupIfAbsent(any());
            verify(changeRequest, never()).approve(any(), any());
            verify(alarmService, never()).sendModificationApprovedEmail(any(), any());
            // AD 반영 자체가 실패했으니 NAS flush를 트리거할 이유가 없다.
            verify(groupService, never()).triggerNasGssFlush(any());
        }

        @Test
        @DisplayName("GROUP 변경 승인 중 AD 반영 후 상태가 바뀌면 DB엔 반영하지 않고 관리자에게 알린 뒤 예외를 던진다(admin_be#554)")
        void approveModification_group_statusChangedAfterExternalCall_alertsAndThrows() throws Exception {
            ChangeRequest changeRequest = mock(ChangeRequest.class);
            Request originalRequest = buildMockedRequestWithStatus(28L, Status.FULFILLED);
            Group newGroup = mock(Group.class);
            when(newGroup.getUbuntuGid()).thenReturn(44L);
            // 1단계(사전 검증)에서는 PENDING, 3단계(외부 호출 완료 후 재검증)에서는 그 사이 다른 관리자가
            // 거절해 DENIED로 바뀐 상황을 시뮬레이션한다.
            when(changeRequest.getStatus()).thenReturn(Status.PENDING, Status.DENIED);
            when(changeRequest.getChangeType()).thenReturn(ChangeType.GROUP);
            when(changeRequest.getNewValue()).thenReturn("[44]");
            when(changeRequest.getRequest()).thenReturn(originalRequest);
            when(changeRequestRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(changeRequest));
            when(userRepository.findById(100L)).thenReturn(Optional.of(mockUser));
            when(groupRepository.findByUbuntuGid(44L)).thenReturn(Optional.of(newGroup));

            ApproveModificationDTO dto = new ApproveModificationDTO(11L, "그룹 변경 승인");

            assertThatThrownBy(() -> service.approveModification(100L, dto))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_REQUEST_STATUS);

            // AD 반영은 이미 끝났다 — 되돌릴 방법이 없으니(candidate 2 API 부재) DB는 그대로 두고 알림만 보낸다.
            verify(groupService).addUserToGroups(eq("testuser"), any());
            verify(mockUser, never()).addGroupIfAbsent(any());
            verify(changeRequest, never()).approve(any(), any());
            verify(alarmService).sendAdminSlackNotification(any(), contains("AD 그룹 반영은 완료됐으나"));
            // AD는 이미 바뀌었으니 우리 DB 커밋 성공 여부와 무관하게 NAS flush는 그대로 트리거해야 한다.
            verify(groupService).triggerNasGssFlush("testuser");
        }

        @Test
        @DisplayName("PORT 변경 요청 승인 시 요청된 각 포트에 대해 portRequestService.createPortRequest()가 호출된다")
        void approveModification_port_success() throws Exception {
            ChangeRequest changeRequest = mock(ChangeRequest.class);
            Request originalRequest = buildMockedRequestWithStatus(24L, Status.FULFILLED);
            when(originalRequest.getResourceGroup()).thenReturn(mockRg);
            when(changeRequest.getStatus()).thenReturn(Status.PENDING);
            when(changeRequest.getChangeType()).thenReturn(ChangeType.PORT);
            when(changeRequest.getNewValue()).thenReturn(
                    "[{\"internalPort\":3000,\"usagePurpose\":\"웹 서버\"},{\"internalPort\":6006,\"usagePurpose\":\"텐서보드\"}]");
            when(changeRequest.getRequest()).thenReturn(originalRequest);
            when(changeRequestRepository.findByIdForUpdate(8L)).thenReturn(Optional.of(changeRequest));
            when(userRepository.findById(100L)).thenReturn(Optional.of(mockUser));

            ApproveModificationDTO dto = new ApproveModificationDTO(8L, "포트 추가 승인");
            service.approveModification(100L, dto);

            ArgumentCaptor<Integer> portCaptor = ArgumentCaptor.forClass(Integer.class);
            ArgumentCaptor<String> purposeCaptor = ArgumentCaptor.forClass(String.class);
            verify(portRequestService, times(2)).createPortRequest(
                    eq(originalRequest), eq(mockRg), portCaptor.capture(), purposeCaptor.capture());

            assertThat(portCaptor.getAllValues()).containsExactly(3000, 6006);
            assertThat(purposeCaptor.getAllValues()).containsExactly("웹 서버", "텐서보드");
            verify(changeRequest).approve(mockUser, "포트 추가 승인");
            verify(alarmService).sendModificationApprovedEmail(changeRequest, "포트 추가 승인");
        }

        @Test
        @DisplayName("변경 값 JSON 파싱 실패 시 INTERNAL_SERVER_ERROR로 감싸서 던지고 상태를 변경하지 않는다")
        void approveModification_invalidJson_wrapsAsInternalServerErrorAndDoesNotApply() throws Exception {
            ChangeRequest changeRequest = mock(ChangeRequest.class);
            Request originalRequest = buildMockedRequestWithStatus(26L, Status.FULFILLED);
            when(changeRequest.getStatus()).thenReturn(Status.PENDING);
            when(changeRequest.getChangeType()).thenReturn(ChangeType.EXPIRES_AT);
            when(changeRequest.getNewValue()).thenReturn("이건-날짜가-아님");
            when(changeRequest.getRequest()).thenReturn(originalRequest);
            when(changeRequestRepository.findByIdForUpdate(15L)).thenReturn(Optional.of(changeRequest));
            when(userRepository.findById(100L)).thenReturn(Optional.of(mockUser));

            ApproveModificationDTO dto = new ApproveModificationDTO(15L, "승인");

            assertThatThrownBy(() -> service.approveModification(100L, dto))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR);

            verify(originalRequest, never()).updateExpiresAt(any());
            verify(changeRequest, never()).approve(any(), anyString());
            verify(alarmService, never()).sendModificationApprovedEmail(any(), any());
        }

        @Test
        @DisplayName("존재하지 않는 변경 요청 ID로 승인하면 BusinessException을 던진다")
        void approveModification_throwsException_whenChangeRequestNotFound() {
            when(changeRequestRepository.findByIdForUpdate(999L)).thenReturn(Optional.empty());

            ApproveModificationDTO dto = new ApproveModificationDTO(999L, "승인");

            assertThatThrownBy(() -> service.approveModification(100L, dto))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("PENDING이 아닌 변경 요청 승인 시 BusinessException을 던진다")
        void approveModification_throwsException_whenNotPendingStatus() {
            ChangeRequest changeRequest = mock(ChangeRequest.class);
            when(changeRequest.getStatus()).thenReturn(Status.FULFILLED);
            when(changeRequestRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(changeRequest));

            ApproveModificationDTO dto = new ApproveModificationDTO(5L, "승인");

            assertThatThrownBy(() -> service.approveModification(100L, dto))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("존재하지 않는 adminId로 승인하면 BusinessException을 던진다")
        void approveModification_throwsException_whenAdminNotFound() {
            ChangeRequest changeRequest = mock(ChangeRequest.class);
            when(changeRequest.getStatus()).thenReturn(Status.PENDING);
            when(changeRequestRepository.findByIdForUpdate(6L)).thenReturn(Optional.of(changeRequest));
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            ApproveModificationDTO dto = new ApproveModificationDTO(6L, "승인");

            assertThatThrownBy(() -> service.approveModification(999L, dto))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("originalRequest가 null이면 BusinessException을 던진다")
        void approveModification_throwsException_whenOriginalRequestIsNull() {
            ChangeRequest changeRequest = mock(ChangeRequest.class);
            when(changeRequest.getStatus()).thenReturn(Status.PENDING);
            when(changeRequest.getRequest()).thenReturn(null);
            when(changeRequestRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(changeRequest));
            when(userRepository.findById(100L)).thenReturn(Optional.of(mockUser));

            ApproveModificationDTO dto = new ApproveModificationDTO(7L, "승인");

            assertThatThrownBy(() -> service.approveModification(100L, dto))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("originalRequest가 FULFILLED가 아니면(DELETED 등) 필드를 건드리지 않고 BusinessException을 던진다")
        void approveModification_originalRequestNotFulfilled_throwsAndDoesNotApply() {
            ChangeRequest changeRequest = mock(ChangeRequest.class);
            Request originalRequest = buildMockedRequestWithStatus(22L, Status.DELETED);
            when(changeRequest.getStatus()).thenReturn(Status.PENDING);
            when(changeRequest.getChangeType()).thenReturn(ChangeType.EXPIRES_AT);
            when(changeRequest.getRequest()).thenReturn(originalRequest);
            when(changeRequestRepository.findByIdForUpdate(8L)).thenReturn(Optional.of(changeRequest));
            when(userRepository.findById(100L)).thenReturn(Optional.of(mockUser));

            ApproveModificationDTO dto = new ApproveModificationDTO(8L, "승인");

            assertThatThrownBy(() -> service.approveModification(100L, dto))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_REQUEST_STATUS);

            verify(originalRequest, never()).updateExpiresAt(any());
            verify(changeRequest, never()).approve(any(), any());
        }
    }

    private Request buildMockedRequestWithStatus(Long requestId, Status status) {
        Request request = mock(Request.class);
        when(request.getRequestId()).thenReturn(requestId);
        when(request.getStatus()).thenReturn(status);
        when(request.getUbuntuUsername()).thenReturn("testuser");
        when(mockUser.getUbuntuPasswordHash()).thenReturn("$6$salt$hash");
        when(request.getRequestGroups()).thenReturn(new LinkedHashSet<>());
        when(request.getUser()).thenReturn(mockUser);
        when(request.getResourceGroup()).thenReturn(mockRg);
        when(request.getContainerImage()).thenReturn(mockImage);
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(requestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));
        return request;
    }
}
