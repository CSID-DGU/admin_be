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
class AdminRequestCommandServiceTest {

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

    private AdminRequestCommandService service;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);

        // @RequiredArgsConstructor 생성자 필드 선언 순서대로 주입
        service = new AdminRequestCommandService(
                alarmService, requestRepository, userRepository, containerImageRepository,
                resourceGroupRepository, podExternalPortRepository, operationJobService,
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
    private Request buildMockedRequest(Long requestId) {
        Request request = mock(Request.class);
        when(request.getRequestId()).thenReturn(requestId);
        // 1단계(승인 시작)에서는 PENDING을 확인하고, 3단계(외부 호출 완료 후 DB 반영)에서는
        // markAsProcessing()으로 바뀐 PROCESSING을 재확인한다. mock이라 실제로 상태가
        // 바뀌진 않으므로, 호출 순서에 맞춰 반환값을 순차 지정한다.
        when(request.getStatus()).thenReturn(Status.PENDING, Status.PROCESSING);
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

    @Nested
    @DisplayName("approveRequest 상태 검증")
    class ApproveRequestValidation {

        @Test
        @DisplayName("PENDING 상태가 아닌 요청 승인 시 BusinessException 발생")
        void approveRequest_nonPendingStatus_throwsBusinessException() {
            Long requestId = 14L;
            Request request = mock(Request.class);
            when(request.getStatus()).thenReturn(Status.FULFILLED);
            when(requestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));

            ApproveRequestDTO dto = new ApproveRequestDTO(requestId, 1L, 1, "승인");

            assertThatThrownBy(() -> service.approveRequest(dto))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_REQUEST_STATUS);

            verify(operationJobService, never()).registerProvision(any());
        }

        @Test
        @DisplayName("존재하지 않는 requestId 승인 시 BusinessException 발생")
        void approveRequest_requestNotFound_throwsBusinessException() {
            when(requestRepository.findByIdForUpdate(999L)).thenReturn(Optional.empty());

            ApproveRequestDTO dto = new ApproveRequestDTO(999L, 1L, 1, "승인");

            assertThatThrownBy(() -> service.approveRequest(dto))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // rejectRequest 테스트
    // ──────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("rejectRequest")
    class RejectRequest {

        @Test
        @DisplayName("PENDING 상태 요청을 거절하면 request.reject()가 호출된다")
        void rejectRequest_success_whenStatusIsPending() {
            Request request = buildMockedRequestWithStatus(30L, Status.PENDING);

            RejectRequestDTO dto = new RejectRequestDTO(30L, "신청서 양식 미흡");
            service.rejectRequest(dto);

            verify(request).reject("신청서 양식 미흡");
        }

        @Test
        @DisplayName("FULFILLED 상태 요청도 거절 가능하다 (현재 정책)")
        void rejectRequest_success_whenStatusIsFulfilled() {
            Request request = buildMockedRequestWithStatus(31L, Status.FULFILLED);

            RejectRequestDTO dto = new RejectRequestDTO(31L, "계정 정책 위반");
            service.rejectRequest(dto);

            verify(request).reject("계정 정책 위반");
        }

        @Test
        @DisplayName("PROCESSING 상태 요청도 거절 가능하다 (승인 진행 중 취소)")
        void rejectRequest_success_whenStatusIsProcessing() {
            Request request = buildMockedRequestWithStatus(34L, Status.PROCESSING);

            RejectRequestDTO dto = new RejectRequestDTO(34L, "승인 취소");
            service.rejectRequest(dto);

            verify(request).reject("승인 취소");
        }

        @Test
        @DisplayName("존재하지 않는 requestId로 거절하면 BusinessException을 던진다")
        void rejectRequest_throwsException_whenRequestNotFound() {
            when(requestRepository.findById(999L)).thenReturn(Optional.empty());

            RejectRequestDTO dto = new RejectRequestDTO(999L, "사유");

            assertThatThrownBy(() -> service.rejectRequest(dto))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("DENIED 상태 요청을 거절하려 하면 BusinessException을 던진다")
        void rejectRequest_throwsException_whenStatusIsDenied() {
            buildMockedRequestWithStatus(32L, Status.DENIED);

            RejectRequestDTO dto = new RejectRequestDTO(32L, "사유");

            assertThatThrownBy(() -> service.rejectRequest(dto))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("DELETED 상태 요청을 거절하려 하면 BusinessException을 던진다")
        void rejectRequest_throwsException_whenStatusIsDeleted() {
            buildMockedRequestWithStatus(33L, Status.DELETED);

            RejectRequestDTO dto = new RejectRequestDTO(33L, "사유");

            assertThatThrownBy(() -> service.rejectRequest(dto))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("거절 성공 시 alarmService.sendRequestRejectedEmail이 호출된다")
        void rejectRequest_sendsRejectionEmail_onSuccess() {
            Request request = buildMockedRequestWithStatus(35L, Status.PENDING);
            RejectRequestDTO dto = new RejectRequestDTO(35L, "신청서 양식 미흡");

            service.rejectRequest(dto);

            verify(alarmService).sendRequestRejectedEmail(request, "신청서 양식 미흡");
        }

        @Test
        @DisplayName("거절 사유가 이메일 발송 메서드에 그대로 전달된다")
        void rejectRequest_passesAdminCommentToEmail() {
            Request request = buildMockedRequestWithStatus(36L, Status.PENDING);
            String comment = "서버 정원 초과로 인한 거절";
            RejectRequestDTO dto = new RejectRequestDTO(36L, comment);

            service.rejectRequest(dto);

            verify(alarmService).sendRequestRejectedEmail(request, comment);
        }

        @Test
        @DisplayName("유효하지 않은 상태로 거절 시 이메일이 발송되지 않는다")
        void rejectRequest_doesNotSendEmail_whenStatusIsInvalid() {
            buildMockedRequestWithStatus(37L, Status.DENIED);
            RejectRequestDTO dto = new RejectRequestDTO(37L, "사유");

            assertThatThrownBy(() -> service.rejectRequest(dto))
                    .isInstanceOf(BusinessException.class);

            verify(alarmService, never()).sendRequestRejectedEmail(any(), anyString());
        }

        @Test
        @DisplayName("이메일 발송 실패 시 예외가 전파되지 않고 reject()는 이미 호출된 상태다")
        void rejectRequest_emailFailure_doesNotPropagateException() {
            Request request = buildMockedRequestWithStatus(38L, Status.PENDING);
            doThrow(new RuntimeException("SMTP 연결 실패"))
                    .when(alarmService).sendRequestRejectedEmail(any(), anyString());

            RejectRequestDTO dto = new RejectRequestDTO(38L, "신청서 양식 미흡");

            // 이메일 실패해도 예외 미전파 (DB 롤백 없음)
            service.rejectRequest(dto);

            verify(request).reject("신청서 양식 미흡");
        }

        @Test
        @DisplayName("이메일 발송 실패 시에도 정상 응답 DTO가 반환된다")
        void rejectRequest_emailFailure_stillReturnsResponseDTO() {
            buildMockedRequestWithStatus(39L, Status.PENDING);
            doThrow(new RuntimeException("MessageUtils 키 없음"))
                    .when(alarmService).sendRequestRejectedEmail(any(), anyString());

            RejectRequestDTO dto = new RejectRequestDTO(39L, "사유");

            // 예외 없이 DTO 반환
            assertThat(service.rejectRequest(dto)).isNotNull();
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

    @Nested
    @DisplayName("작업 등록 승인")
    class AsyncApproval {

        @BeforeEach
        void enableAsyncApproval() {
            when(containerImageRepository.findById(1L)).thenReturn(Optional.of(mockImage));
            when(resourceGroupRepository.findById(1)).thenReturn(Optional.of(mockRg));
        }

        private Request processingRequest(Long requestId) {
            Request request = mock(Request.class);
            when(request.getRequestId()).thenReturn(requestId);
            when(request.getStatus()).thenReturn(Status.PROCESSING);
            when(request.getUser()).thenReturn(mockUser);
            when(request.getResourceGroup()).thenReturn(mockRg);
            when(request.getContainerImage()).thenReturn(mockImage);
            when(requestRepository.findById(requestId)).thenReturn(Optional.of(request));
            when(requestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));
            return request;
        }

        @Test
        @DisplayName("계정이 없는 사용자는 계정 정보를 담아 생성 작업으로 등록하고, 동기 경로는 타지 않는다")
        void registersProvisionWithAccount() {
            // Given
            Long requestId = 201L;
            Request request = buildMockedRequest(requestId);

            // When
            service.approveRequest(new ApproveRequestDTO(requestId, 1L, 1, "승인합니다"));

            // Then
            ArgumentCaptor<ProvisionRegisterRequestDTO> captor =
                    ArgumentCaptor.forClass(ProvisionRegisterRequestDTO.class);
            verify(operationJobService).registerProvision(captor.capture());
            ProvisionRegisterRequestDTO body = captor.getValue();
            assertThat(body.requestId()).isEqualTo(requestId);
            assertThat(body.username()).isEqualTo("testuser");
            assertThat(body.account()).isNotNull();
            assertThat(body.account().passwordHash()).isEqualTo("$6$salt$hash");
            assertThat(body.account().primaryGroupName()).isEqualTo("testuser");
            assertThat(body.account().gecos()).isEqualTo("테스트유저");

            // 관리자가 고른 값은 미리 남기고, 승인 확정은 작업이 성공한 뒤에 한다.
            verify(request).markAsProcessing();
            verify(request).prepareAsyncApproval(mockImage, mockRg, "승인합니다");
            verify(request, never()).completeApproval();
        }

        @Test
        @DisplayName("계정이 있는 사용자는 계정 정보를 빼고 등록하고, 이번 신청의 그룹은 작업 등록 DTO에 실어 보낸다")
        void registersPodOnlyWhenAccountExists() {
            // Given
            Long requestId = 202L;
            buildMockedRequest(requestId);
            when(mockUser.hasUbuntuAccount()).thenReturn(true);

            // When
            service.approveRequest(new ApproveRequestDTO(requestId, 1L, 1, null));

            // Then
            ArgumentCaptor<ProvisionRegisterRequestDTO> captor =
                    ArgumentCaptor.forClass(ProvisionRegisterRequestDTO.class);
            verify(operationJobService).registerProvision(captor.capture());
            assertThat(captor.getValue().account()).isNull();
            assertThat(captor.getValue().supplementaryGroups()).isEmpty();
            // 계정을 새로 만들 때는 작업이 그룹까지 넣지만, 재사용 계정은 config-server의
            // provision 제어기가 Pod 생성 후 그룹을 추가하므로 로컬에서는 호출하지 않는다.
            verify(groupService, never()).addUserToGroups(anyString(), anyList());
        }

        @Test
        @DisplayName("응답은 트랜잭션 안에서 만든다 — 커밋 뒤 lazy 연관(user.userGroups)을 읽으면 승인은 됐는데 500이 난다")
        void buildsResponseBeforeCommit() {
            // Given
            Long requestId = 205L;
            buildMockedRequest(requestId);
            java.util.concurrent.atomic.AtomicBoolean committed = new java.util.concurrent.atomic.AtomicBoolean(false);
            doAnswer(inv -> { committed.set(true); return null; }).when(transactionManager).commit(any());
            when(mockUser.getUserGroups()).thenAnswer(inv -> {
                if (committed.get()) {
                    throw new org.hibernate.LazyInitializationException("could not initialize proxy - no Session");
                }
                return new java.util.HashSet<>();
            });

            // When & Then
            assertThat(service.approveRequest(new ApproveRequestDTO(requestId, 1L, 1, null))).isNotNull();
        }

        @Test
        @DisplayName("작업 등록이 실패하면 신청을 PENDING으로 되돌린다")
        void revertsWhenRegistrationFails() {
            // Given
            Long requestId = 203L;
            Request request = buildMockedRequest(requestId);
            doThrow(new BusinessException(ErrorCode.POD_CREATION_FAILED))
                    .when(operationJobService).registerProvision(any());

            // When & Then
            assertThatThrownBy(() -> service.approveRequest(new ApproveRequestDTO(requestId, 1L, 1, null)))
                    .isInstanceOf(BusinessException.class);
            verify(request).revertToPending();
        }

        @Test
        @DisplayName("작업이 성공하면 계정·컨테이너 정보와 포트를 반영하고 승인을 확정한다")
        void completesApprovalFromJobResult() {
            // Given
            Long requestId = 204L;
            Request request = processingRequest(requestId);
            when(mockUser.getUbuntuUid()).thenReturn(50001L);
            when(mockUser.getUbuntuGid()).thenReturn(50001L);
            when(podExternalPortRepository.save(any(PodExternalPort.class))).thenAnswer(inv -> inv.getArgument(0));

            JobResultResponseDTO.Result made = new JobResultResponseDTO.Result(
                    50001L, 50001L, "ailab-testuser-abcd", "farm2",
                    List.of(new CreatePodResponseDTO.PortInfo("ssh", 22, 32001),
                            new CreatePodResponseDTO.PortInfo("jupyter", 8888, 32002)));

            // When
            service.completeApprovalJob(requestId, made);

            // Then
            verify(mockUser).assignUbuntuAccount(50001L, 50001L);
            verify(request).assignUbuntuIds(50001L, 50001L);
            verify(request).assignPodInfo("ailab-testuser-abcd", "farm2");
            verify(request).completeApproval();
            verify(podExternalPortRepository, times(2)).save(any(PodExternalPort.class));
            verify(alarmService).sendContainerCreatedEmail(request, "32001", "32002");
        }

        @Test
        @DisplayName("작업이 성공하면 이 신청이 요청한 그룹이 계정(User)에 누적된다")
        void completeApprovalJob_mergesRequestedGroupsIntoUser() {
            // Given
            Long requestId = 207L;
            Request request = processingRequest(requestId);
            Group groupA = mock(Group.class);
            Group groupB = mock(Group.class);
            RequestGroup rgA = mock(RequestGroup.class);
            RequestGroup rgB = mock(RequestGroup.class);
            when(rgA.getGroup()).thenReturn(groupA);
            when(rgB.getGroup()).thenReturn(groupB);
            when(request.getRequestGroups()).thenReturn(new LinkedHashSet<>(List.of(rgA, rgB)));
            when(podExternalPortRepository.save(any(PodExternalPort.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            service.completeApprovalJob(requestId, new JobResultResponseDTO.Result(
                    50001L, 50001L, "ailab-testuser-abcd", "farm2", List.of()));

            // Then - request_groups(신청 시점 그룹)는 그대로 두고, 계정에도 누적한다(교체 아님).
            verify(mockUser).addGroupIfAbsent(groupA);
            verify(mockUser).addGroupIfAbsent(groupB);
        }

        @Test
        @DisplayName("작업이 도는 사이 신청 상태가 바뀌었으면 승인을 확정하지 않는다")
        void doesNotCompleteWhenStatusChanged() {
            // Given - 다른 관리자가 거절해 PROCESSING이 아닌 신청
            Long requestId = 205L;
            Request request = mock(Request.class);
            when(request.getStatus()).thenReturn(Status.DENIED);
            when(requestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));

            // When
            service.completeApprovalJob(requestId, new JobResultResponseDTO.Result(
                    null, null, "ailab-testuser-abcd", "farm2", List.of()));

            // Then
            verify(request, never()).completeApproval();
            verify(alarmService, never()).sendContainerCreatedEmail(any(), any(), any());
        }

        @Test
        @DisplayName("작업 결과에 자원 정보가 없으면 확정하지 않고 관리자에게 알린다")
        void reportsWhenResultMissing() {
            // Given
            Long requestId = 206L;
            Request request = processingRequest(requestId);

            // When
            service.completeApprovalJob(requestId, null);

            // Then
            verify(request, never()).completeApproval();
            verify(alarmService).sendAdminSlackNotification(any(), contains("결과 정보를 받지 못해"));
        }

        @Test
        @DisplayName("작업이 실패하면 신청을 PENDING으로 되돌리고 알린다")
        void failRevertsToPending() {
            // Given
            Long requestId = 207L;
            Request request = processingRequest(requestId);
            JobResultResponseDTO result = new JobResultResponseDTO(
                    String.valueOf(requestId), "provision", 9L, "FAIL", "KDC_FAILED", null, null);

            // When
            service.failApprovalJob(requestId, result);

            // Then
            verify(request).revertToPending();
            verify(alarmService).sendAdminSlackNotification(any(), contains("KDC_FAILED"));
        }

        @Test
        @DisplayName("자원을 남긴 실패(DEGRADED)는 되돌리지 않고 자원이 남았다고 알린다")
        void degradedKeepsRequestUntouched() {
            Long requestId = 209L;
            Request request = processingRequest(requestId);
            JobResultResponseDTO result = new JobResultResponseDTO(
                    String.valueOf(requestId), "provision", 183L, "FAIL", "DEGRADED", null, null);

            service.reportDegradedApprovalJob(requestId, result);

            verify(request, never()).revertToPending();
            verify(alarmService).sendAdminSlackNotification(any(), contains("자원은 남아 있습니다"));
        }

        @Test
        @DisplayName("결과 불명이면 되돌리지 않고 관리자 확인 대상으로만 알린다")
        void unknownKeepsRequestUntouched() {
            // Given - 자원이 남아 있을 수 있어 되돌리면 재승인 때 중복 생성이 된다.
            Long requestId = 208L;
            Request request = processingRequest(requestId);
            JobResultResponseDTO result = new JobResultResponseDTO(
                    String.valueOf(requestId), "provision", 10L, "UNKNOWN", "WAIT_READY_TIMEOUT", null, null);

            // When
            service.reportUnknownApprovalJob(requestId, result);

            // Then
            verify(request, never()).revertToPending();
            verify(alarmService).sendAdminSlackNotification(any(), contains("결과가 불명"));
        }
    }
}
