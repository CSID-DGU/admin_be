package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.support.Alerts;
import DGU_AI_LAB.admin_be.global.alert.InMemoryAlertDeduplicator;
import DGU_AI_LAB.admin_be.domain.requests.job.JobClient;
import DGU_AI_LAB.admin_be.domain.requests.job.JobResults;
import DGU_AI_LAB.admin_be.domain.alarm.service.AlarmService;
import DGU_AI_LAB.admin_be.domain.containerImage.entity.ContainerImage;
import DGU_AI_LAB.admin_be.domain.containerImage.repository.ContainerImageRepository;
import DGU_AI_LAB.admin_be.domain.groups.entity.Group;
import DGU_AI_LAB.admin_be.domain.groups.repository.GroupRepository;
import DGU_AI_LAB.admin_be.domain.groups.service.GroupService;
import DGU_AI_LAB.admin_be.domain.groups.service.PendingGroupService;
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
import DGU_AI_LAB.admin_be.domain.requests.entity.RequestGroupId;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.repository.ChangeRequestRepository;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestGroupRepository;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup;
import DGU_AI_LAB.admin_be.domain.resourceGroups.repository.ResourceGroupRepository;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.entity.PasswordResetRequest;
import DGU_AI_LAB.admin_be.domain.users.entity.PasswordResetStatus;
import DGU_AI_LAB.admin_be.domain.users.repository.PasswordResetRequestRepository;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
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
    @Mock private PasswordResetRequestRepository passwordResetRequestRepository;
    @Mock private ContainerImageRepository containerImageRepository;
    @Mock private ResourceGroupRepository resourceGroupRepository;
    @Mock private ChangeRequestRepository changeRequestRepository;
    @Mock private GroupRepository groupRepository;
    @Mock private RequestGroupRepository requestGroupRepository;
    @Mock private GroupService groupService;
    @Mock private PodExternalPortRepository podExternalPortRepository;
    @Mock private JobClient jobClient;
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
                alarmService, requestRepository, userRepository, passwordResetRequestRepository,
                containerImageRepository,
                resourceGroupRepository, podExternalPortRepository, jobClient,
                new PendingGroupService(groupRepository, requestGroupRepository, mock(EntityManager.class)),
                transactionManager, new InMemoryAlertDeduplicator()
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

            verify(jobClient, never()).registerProvision(any());
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
        @DisplayName("FULFILLED 요청은 거절할 수 없다 — 거절은 컨테이너를 회수하지 않는다")
        void rejectRequest_throws_whenStatusIsFulfilled() {
            Request request = buildMockedRequestWithStatus(31L, Status.FULFILLED);

            assertThatThrownBy(() -> service.rejectRequest(new RejectRequestDTO(31L, "계정 정책 위반")))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_REQUEST_STATUS);
            verify(request, never()).reject(any());
        }

        @Test
        @DisplayName("PROCESSING 요청은 생성 작업이 실패로 끝났으면 거절할 수 있다")
        void rejectRequest_success_whenProvisionJobFailed() {
            Request request = buildMockedRequestWithStatus(34L, Status.PROCESSING);
            when(jobClient.getResult(JobResults.KIND_PROVISION, 34L))
                    .thenReturn(job("FAIL", null));

            service.rejectRequest(new RejectRequestDTO(34L, "승인 취소"));

            verify(request).reject("승인 취소");
        }

        @Test
        @DisplayName("PROCESSING 요청은 생성 작업이 도는 중이면 거절할 수 없다")
        void rejectRequest_throws_whenProvisionJobRunning() {
            Request request = buildMockedRequestWithStatus(35L, Status.PROCESSING);
            when(jobClient.getResult(JobResults.KIND_PROVISION, 35L))
                    .thenReturn(job("START", null));

            assertThatThrownBy(() -> service.rejectRequest(new RejectRequestDTO(35L, "취소")))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PROVISION_JOB_IN_PROGRESS);
            verify(request, never()).reject(any());
        }

        @Test
        @DisplayName("PROCESSING 요청은 단계 재시도(RETRY) 중이어도 거절할 수 없다 — START만 보면 재시도 중인 작업을 놓친다")
        void rejectRequest_throws_whenProvisionJobRetrying() {
            Request request = buildMockedRequestWithStatus(40L, Status.PROCESSING);
            when(jobClient.getResult(JobResults.KIND_PROVISION, 40L))
                    .thenReturn(job("RETRY", null));

            assertThatThrownBy(() -> service.rejectRequest(new RejectRequestDTO(40L, "취소")))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PROVISION_JOB_IN_PROGRESS);
            verify(request, never()).reject(any());
        }

        @Test
        @DisplayName("PROCESSING 요청은 작업이 성공했는데 아직 반영 전이면 거절할 수 없다")
        void rejectRequest_throws_whenProvisionSucceededButNotApplied() {
            Request request = buildMockedRequestWithStatus(36L, Status.PROCESSING);
            when(jobClient.getResult(JobResults.KIND_PROVISION, 36L))
                    .thenReturn(job("SUCCESS", new JobResultResponseDTO.Result(1L, 1L, "ailab-testuser-x", "farm2", List.of())));

            assertThatThrownBy(() -> service.rejectRequest(new RejectRequestDTO(36L, "취소")))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PROVISION_JOB_IN_PROGRESS);
            verify(request, never()).reject(any());
        }

        @Test
        @DisplayName("성공 결과를 잃어 갇힌 PROCESSING 요청은 거절할 수 있다")
        void rejectRequest_success_whenSuccessResultMissing() {
            Request request = buildMockedRequestWithStatus(37L, Status.PROCESSING);
            when(jobClient.getResult(JobResults.KIND_PROVISION, 37L))
                    .thenReturn(job("SUCCESS", null));

            service.rejectRequest(new RejectRequestDTO(37L, "정리"));

            verify(request).reject("정리");
        }

        @Test
        @DisplayName("방금 승인돼 작업 번호가 아직 기록되지 않은 PROCESSING 요청은 거절할 수 없다")
        void rejectRequest_throws_whenAwaitingRegistration() {
            Request request = buildMockedRequestWithStatus(38L, Status.PROCESSING);
            when(request.getJobId()).thenReturn(null);
            when(request.getUpdatedAt()).thenReturn(LocalDateTime.now());

            assertThatThrownBy(() -> service.rejectRequest(new RejectRequestDTO(38L, "취소")))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PROVISION_JOB_IN_PROGRESS);
            verify(jobClient, never()).getResult(any(), any());
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

    private static JobResultResponseDTO job(String phase, JobResultResponseDTO.Result result) {
        return job(phase, result, 1L);
    }

    private static JobResultResponseDTO job(String phase, JobResultResponseDTO.Result result, Long jobId) {
        return new JobResultResponseDTO("0", "provision", jobId, phase, null, null, result);
    }

    private Request buildMockedRequestWithStatus(Long requestId, Status status) {
        Request request = mock(Request.class);
        when(request.getRequestId()).thenReturn(requestId);
        when(request.getStatus()).thenReturn(status);
        when(request.getJobId()).thenReturn(1L);   // job()이 돌려주는 작업 번호와 같은 작업
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
            verify(jobClient).registerProvision(captor.capture());
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
        @DisplayName("자기 자신만 PROCESSING이면(정상적인 단독 승인) 충돌로 보지 않고 그대로 등록한다")
        void doesNotConflictWithItself() {
            Long requestId = 220L;
            Request request = buildMockedRequest(requestId);
            // findAllByUser_UserIdAndStatus가 이 신청 자신만 돌려준다 — markAsProcessing 뒤의 정상 상태.
            when(requestRepository.findAllByUser_UserIdAndStatus(100L, Status.PROCESSING))
                    .thenReturn(List.of(request));

            service.approveRequest(new ApproveRequestDTO(requestId, 1L, 1, null));

            verify(jobClient).registerProvision(any());
            verify(request, never()).revertToPending();
        }

        @Test
        @DisplayName("같은 사용자의 다른 신청이 이미 처리 중이면 등록을 보내지 않고 PENDING으로 되돌린다(#607) — " +
                "돌아온 사용자가 두 신청 다 같은 uid로 성공해 계정 하나에 컨테이너 두 개가 붙는 것을 막는다")
        void rejectsWhenAnotherRequestOfSameUserIsAlreadyProcessing() {
            Long requestId = 221L;
            Request request = buildMockedRequest(requestId);
            Request otherProcessing = mock(Request.class);
            when(otherProcessing.getRequestId()).thenReturn(999L); // 같은 사용자의 다른 신청
            when(requestRepository.findAllByUser_UserIdAndStatus(100L, Status.PROCESSING))
                    .thenReturn(List.of(request, otherProcessing));

            assertThatThrownBy(() -> service.approveRequest(new ApproveRequestDTO(requestId, 1L, 1, null)))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_APPROVAL_ALREADY_IN_PROGRESS)
                    .hasMessageContaining("[999]");

            verify(jobClient, never()).registerProvision(any());
            verify(request).revertToPending();
        }

        @Test
        @DisplayName("계정 회수가 도는 중인 사용자는 승인을 거절한다 — 새 컨테이너가 곧 지워질 계정을 쓰게 된다")
        void rejectsWhileAccountReleasing() {
            Long requestId = 206L;
            buildMockedRequest(requestId);
            when(mockUser.isReleasingUbuntuAccount()).thenReturn(true);

            assertThatThrownBy(() -> service.approveRequest(new ApproveRequestDTO(requestId, 1L, 1, null)))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UBUNTU_ACCOUNT_RELEASING);
            verify(jobClient, never()).registerProvision(any());
        }

        @Test
        @DisplayName("비밀번호 교체 작업이 도는 중인 사용자는 승인을 거절한다 — 새 컨테이너만 옛 비밀번호로 만들어진다")
        void rejectsWhilePasswordResetInProgress() {
            Long requestId = 207L;
            buildMockedRequest(requestId);
            when(passwordResetRequestRepository.findAllByUserIdAndStatusForShare(100L, PasswordResetStatus.PROCESSING))
                    .thenReturn(List.of(mock(PasswordResetRequest.class)));

            assertThatThrownBy(() -> service.approveRequest(new ApproveRequestDTO(requestId, 1L, 1, null)))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PASSWORD_RESET_IN_PROGRESS);
            verify(jobClient, never()).registerProvision(any());
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
            verify(jobClient).registerProvision(captor.capture());
            assertThat(captor.getValue().account()).isNull();
            assertThat(captor.getValue().supplementaryGroups()).isEmpty();
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
                    .when(jobClient).registerProvision(any());

            // When & Then
            assertThatThrownBy(() -> service.approveRequest(new ApproveRequestDTO(requestId, 1L, 1, null)))
                    .isInstanceOf(BusinessException.class);
            verify(request).revertToPending();
        }

        @Test
        @DisplayName("등록 응답은 실패했지만 작업이 도는 중이면 되돌리지 않고 그 작업을 이어받는다")
        void adoptsRunningJobWhenRegistrationResponseFails() {
            Long requestId = 205L;
            Request request = buildMockedRequest(requestId);
            doThrow(new BusinessException(ErrorCode.POD_CREATION_FAILED))
                    .when(jobClient).registerProvision(any());
            when(jobClient.getResult(JobResults.KIND_PROVISION, requestId))
                    .thenReturn(job("START", null, 77L));

            service.approveRequest(new ApproveRequestDTO(requestId, 1L, 1, null));

            verify(request, never()).revertToPending();
            verify(request).recordJob(77L);
        }

        @Test
        @DisplayName("등록 응답은 실패했지만 작업이 단계 재시도(RETRY) 중이면 되돌리지 않고 이어받는다")
        void adoptsRetryingJobWhenRegistrationResponseFails() {
            Long requestId = 210L;
            Request request = buildMockedRequest(requestId);
            doThrow(new BusinessException(ErrorCode.POD_CREATION_FAILED))
                    .when(jobClient).registerProvision(any());
            when(jobClient.getResult(JobResults.KIND_PROVISION, requestId))
                    .thenReturn(job("RETRY", null, 78L));

            service.approveRequest(new ApproveRequestDTO(requestId, 1L, 1, null));

            verify(request, never()).revertToPending();
            verify(request).recordJob(78L);
        }

        @Test
        @DisplayName("config-server에 연결조차 못 했으면 작업이 없는 것이 확실하므로 조회 없이 바로 PENDING으로 되돌린다")
        void revertsImmediatelyWhenServerUnreachable() {
            Long requestId = 208L;
            Request request = buildMockedRequest(requestId);
            doThrow(new BusinessException("작업 등록 중 오류", ErrorCode.POD_CREATION_FAILED,
                    new RuntimeException(new java.net.ConnectException("Connection refused"))))
                    .when(jobClient).registerProvision(any());

            assertThatThrownBy(() -> service.approveRequest(new ApproveRequestDTO(requestId, 1L, 1, null)))
                    .isInstanceOf(BusinessException.class);
            verify(request).revertToPending();
            verify(jobClient, never()).getResult(any(), any());
        }

        @Test
        @DisplayName("등록 실패 후 작업 상태도 조회하지 못하면 되돌리지 않고 오류를 전파한다(재조정이 판단)")
        void keepsProcessingWhenJobLookupAlsoFails() {
            Long requestId = 206L;
            Request request = buildMockedRequest(requestId);
            doThrow(new BusinessException(ErrorCode.POD_CREATION_FAILED))
                    .when(jobClient).registerProvision(any());
            when(jobClient.getResult(JobResults.KIND_PROVISION, requestId))
                    .thenThrow(new BusinessException(ErrorCode.EXTERNAL_API_ERROR));

            assertThatThrownBy(() -> service.approveRequest(new ApproveRequestDTO(requestId, 1L, 1, null)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.POD_CREATION_FAILED);
            verify(request, never()).revertToPending();
        }

        @Test
        @DisplayName("성공 결과가 없으면 반영하지 않고 알림은 신청마다 한 번만 보낸다")
        void missingSuccessResultAlertsOnce() {
            Long requestId = 207L;
            Request request = processingRequest(requestId);

            service.completeApprovalJob(requestId, null);
            service.completeApprovalJob(requestId, null);

            assertThat(Alerts.needsAction(alarmService)).hasSize(1);
            verify(request, never()).completeApproval();
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
            verify(request).assignPodInfo("ailab-testuser-abcd", "farm2");
            verify(request).completeApproval();
            verify(podExternalPortRepository, times(2)).save(any(PodExternalPort.class));
            verify(alarmService).sendContainerCreatedEmail(request, "32001", "32002");
            verify(alarmService).sendContainerCreatedNotification(request, "32001", "32002");
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
            verify(alarmService, never()).sendContainerCreatedNotification(any(), any(), any());
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
            assertThat(Alerts.needsAction(alarmService)).filteredOn(alert -> alert.contains("approval.result-missing")).hasSize(1);
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
            assertThat(Alerts.needsAction(alarmService)).filteredOn(alert -> alert.contains("KDC_FAILED")).hasSize(1);
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
            assertThat(Alerts.needsAction(alarmService)).filteredOn(alert -> alert.contains("approval.job-degraded")).hasSize(1);
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
            assertThat(Alerts.needsAction(alarmService)).filteredOn(alert -> alert.contains("approval.job-unknown")).hasSize(1);
        }
    }
    @Nested
    @DisplayName("승인 대기 그룹(gid 없는 새 공유 그룹)")
    class PendingGroups {

        private Group pending;
        private Group created;

        @BeforeEach
        void setUpGroups() {
            pending = Group.builder().groupName("vision-lab").build();
            ReflectionTestUtils.setField(pending, "groupId", 3L);
            created = Group.builder().groupName("ailab").ubuntuGid(2001L).build();
            ReflectionTestUtils.setField(created, "groupId", 4L);
            when(containerImageRepository.findById(1L)).thenReturn(Optional.of(mockImage));
            when(resourceGroupRepository.findById(1)).thenReturn(Optional.of(mockRg));
            when(jobClient.registerProvision(any())).thenReturn(9001L);
        }

        /** 생성 작업이 돌고 있는(PROCESSING) 신청. */
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

        /** 신청이 고른 그룹 기록. 서비스는 그룹 번호를 기록의 키에서 꺼내 잠그며 다시 읽는다. */
        private void choose(Request request, Long requestId, Group... groups) {
            LinkedHashSet<RequestGroup> chosen = new LinkedHashSet<>();
            for (Group g : groups) {
                RequestGroup rg = mock(RequestGroup.class);
                when(rg.getId()).thenReturn(new RequestGroupId(requestId, g.getGroupId()));
                when(rg.getGroup()).thenReturn(g);
                chosen.add(rg);
            }
            when(request.getRequestGroups()).thenReturn(chosen);
            when(groupRepository.findAllByIdForUpdate(any())).thenReturn(List.of(groups));
        }

        @Test
        @DisplayName("승인하면 승인 대기 그룹은 gid 없이 이름만, 만들어진 그룹은 gid 와 함께 생성 작업에 싣는다")
        void approvalSendsPendingGroupsByNameOnly() throws Exception {
            Long requestId = 301L;
            Request request = buildMockedRequest(requestId);
            choose(request, requestId, pending, created);

            service.approveRequest(new ApproveRequestDTO(requestId, 1L, 1, null));

            ArgumentCaptor<ProvisionRegisterRequestDTO> captor = ArgumentCaptor.forClass(ProvisionRegisterRequestDTO.class);
            verify(jobClient).registerProvision(captor.capture());
            List<UserCreationRequestDTO.SupplementaryGroup> groups = captor.getValue().account().supplementaryGroups();
            assertThat(groups).containsExactly(
                    new UserCreationRequestDTO.SupplementaryGroup("vision-lab", null),
                    new UserCreationRequestDTO.SupplementaryGroup("ailab", 2001L));
            // gid 칸 자체를 빼고 보낸다 — config-server 가 "gid 없음 = 새로 만들 그룹"으로 읽는다.
            String json = new ObjectMapper().writeValueAsString(captor.getValue());
            assertThat(json).contains("{\"name\":\"vision-lab\"}").contains("{\"name\":\"ailab\",\"gid\":2001}");
        }

        @Test
        @DisplayName("같은 승인 대기 그룹을 다른 신청이 만드는 중이면 승인을 막고 작업을 등록하지 않는다")
        void approvalBlockedWhileAnotherRequestCreatesTheGroup() {
            Long requestId = 302L;
            Request request = buildMockedRequest(requestId);
            choose(request, requestId, pending);
            Request other = mock(Request.class);
            when(other.getRequestId()).thenReturn(399L);
            when(other.getStatus()).thenReturn(Status.PROCESSING);
            RequestGroup othersChoice = mock(RequestGroup.class);
            when(othersChoice.getRequest()).thenReturn(other);
            when(requestGroupRepository.findAllByGroupIdsForUpdate(List.of(3L))).thenReturn(List.of(othersChoice));

            assertThatThrownBy(() -> service.approveRequest(new ApproveRequestDTO(requestId, 1L, 1, null)))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PENDING_GROUP_IN_PROGRESS);
            verify(jobClient, never()).registerProvision(any());
            // 승인 시작(PROCESSING) 표시는 같은 트랜잭션이라 함께 롤백된다.
            verify(transactionManager).rollback(transactionStatus);
        }

        @Test
        @DisplayName("작업이 성공하면 결과의 gid 로 승인 대기 그룹을 채우고 승인을 확정한다")
        void completionFillsGidAndCompletes() {
            Long requestId = 303L;
            Request request = processingRequest(requestId);
            choose(request, requestId, pending, created);
            when(groupRepository.findByUbuntuGid(70002L)).thenReturn(Optional.empty());

            service.completeApprovalJob(requestId, new JobResultResponseDTO.Result(
                    50001L, 50001L, "ailab-testuser-abcd", "farm2", List.of(),
                    null, null, null, null, null, null,
                    List.of(new JobResultResponseDTO.GroupResult("vision-lab", 70002L),
                            new JobResultResponseDTO.GroupResult("ailab", 2001L))));

            assertThat(pending.getUbuntuGid()).isEqualTo(70002L);
            assertThat(created.getUbuntuGid()).isEqualTo(2001L);
            verify(request).completeApproval();
            verify(mockUser).addGroupIfAbsent(pending);
            verify(mockUser).addGroupIfAbsent(created);
        }

        @Test
        @DisplayName("결과에 승인 대기 그룹의 gid 가 없으면 확정하지 않고 롤백하며, 매 바퀴 다시 불려도 알림은 한 번만 보낸다")
        void completionWithoutGidHaltsAndAlertsOnce() {
            Long requestId = 304L;
            Request request = processingRequest(requestId);
            choose(request, requestId, pending);
            // 예전 config-server 처럼 groups 를 돌려주지 않는 결과
            JobResultResponseDTO.Result result = new JobResultResponseDTO.Result(
                    50001L, 50001L, "ailab-testuser-abcd", "farm2", List.of());

            service.completeApprovalJob(requestId, result);
            service.completeApprovalJob(requestId, result);

            assertThat(pending.getUbuntuGid()).isNull();
            verify(request, never()).completeApproval();
            verify(request, never()).assignPodInfo(any(), any());
            verify(mockUser, never()).assignUbuntuAccount(any(), any());
            verify(transactionStatus, times(2)).setRollbackOnly();
            assertThat(Alerts.needsAction(alarmService)).filteredOn(alert -> alert.contains("vision-lab")).hasSize(1);
            verify(alarmService, never()).sendContainerCreatedEmail(any(), any(), any());
        }

        @Test
        @DisplayName("결과의 gid 를 다른 그룹이 이미 쓰고 있으면 확정하지 않는다")
        void completionWithConflictingGidHalts() {
            Long requestId = 305L;
            Request request = processingRequest(requestId);
            choose(request, requestId, pending);
            when(groupRepository.findByUbuntuGid(2001L)).thenReturn(Optional.of(created));

            service.completeApprovalJob(requestId, new JobResultResponseDTO.Result(
                    50001L, 50001L, "ailab-testuser-abcd", "farm2", List.of(),
                    null, null, null, null, null, null,
                    List.of(new JobResultResponseDTO.GroupResult("vision-lab", 2001L))));

            assertThat(pending.getUbuntuGid()).isNull();
            verify(request, never()).completeApproval();
            assertThat(Alerts.needsAction(alarmService)).filteredOn(alert -> alert.contains("ailab")).hasSize(1);
        }

        @Test
        @DisplayName("신청을 거절하면 이 신청이 고른 승인 대기 그룹을 정리한다 — 다른 신청이 고르고 있지 않으면 지운다")
        void rejectionDeletesAbandonedPendingGroup() {
            Request request = buildMockedRequestWithStatus(306L, Status.PENDING);
            choose(request, 306L, pending, created);
            when(requestGroupRepository.findAllByGroupIdsForUpdate(List.of(3L))).thenReturn(List.of());

            service.rejectRequest(new RejectRequestDTO(306L, "반려"));

            verify(request).reject("반려");
            verify(groupRepository).deletePendingById(3L);
            verify(groupRepository, never()).deletePendingById(4L);
        }
    }

    @Nested
    @DisplayName("같은 사용자 승인 직렬화 잠금")
    class ApprovalLockStripes {

        private static final long WAIT_SECONDS = 5;

        @BeforeEach
        void stubApprovalLookups() {
            when(containerImageRepository.findById(1L)).thenReturn(Optional.of(mockImage));
            when(resourceGroupRepository.findById(1)).thenReturn(Optional.of(mockRg));
        }

        private User userWithId(long userId) {
            User user = mock(User.class);
            when(user.getUserId()).thenReturn(userId);
            when(user.getName()).thenReturn("user-" + userId);
            when(user.getUbuntuPasswordHash()).thenReturn("$6$salt$hash");
            when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
            return user;
        }

        private void pendingRequestOf(Long requestId, User owner) {
            Request request = mock(Request.class);
            when(request.getRequestId()).thenReturn(requestId);
            when(request.getStatus()).thenReturn(Status.PENDING, Status.PROCESSING);
            when(request.getUbuntuUsername()).thenReturn("user" + requestId);
            when(request.getRequestGroups()).thenReturn(new LinkedHashSet<>());
            when(request.getUser()).thenReturn(owner);
            when(request.getResourceGroup()).thenReturn(mockRg);
            when(request.getContainerImage()).thenReturn(mockImage);
            when(requestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));
        }

        /** 지정한 신청의 등록 호출에서 풀어 줄 때까지 멈춘다. 멈춘 동안 그 스레드는 사용자 잠금을 쥐고 있다. */
        private java.util.concurrent.CountDownLatch[] holdRegistrationOf(Long heldRequestId) {
            java.util.concurrent.CountDownLatch entered = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.atomic.AtomicLong nextJobId = new java.util.concurrent.atomic.AtomicLong(7000L);
            when(jobClient.registerProvision(any())).thenAnswer(inv -> {
                ProvisionRegisterRequestDTO body = inv.getArgument(0);
                if (heldRequestId.equals(body.requestId())) {
                    entered.countDown();
                    if (!release.await(WAIT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)) {
                        throw new AssertionError("release timeout");
                    }
                }
                return nextJobId.incrementAndGet();
            });
            return new java.util.concurrent.CountDownLatch[]{entered, release};
        }

        private Thread approveAsync(Long requestId, List<Throwable> failures) {
            Thread t = new Thread(() -> {
                try {
                    service.approveRequest(new ApproveRequestDTO(requestId, 1L, 1, null));
                } catch (Throwable e) {
                    failures.add(e);
                }
            }, "approve-" + requestId);
            t.start();
            return t;
        }

        private void awaitBlocked(Thread t) throws InterruptedException {
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
            while (t.getState() != Thread.State.BLOCKED) {
                if (System.nanoTime() > deadline) {
                    throw new AssertionError(t.getName() + " did not block, state=" + t.getState());
                }
                Thread.sleep(5);
            }
        }

        private void joinAll(Thread... threads) throws InterruptedException {
            for (Thread t : threads) {
                t.join(java.util.concurrent.TimeUnit.SECONDS.toMillis(WAIT_SECONDS));
                assertThat(t.isAlive()).as(t.getName() + " finished").isFalse();
            }
        }

        @Test
        @DisplayName("잠금 칸 번호는 어떤 userId(0·음수·극값 포함)에도 [0, 칸 수) 안에 든다")
        void stripeAlwaysInRange() {
            long[] ids = {0L, 1L, -1L, 63L, 64L, 65L, 100L, 164L, Integer.MAX_VALUE, Integer.MIN_VALUE,
                    Long.MAX_VALUE, Long.MIN_VALUE, 1L << 32, (1L << 32) + 1, -(1L << 40)};
            for (long id : ids) {
                assertThat(AdminRequestCommandService.approvalLockStripe(id))
                        .as("userId=%d", id)
                        .isBetween(0, AdminRequestCommandService.APPROVAL_LOCK_STRIPES - 1);
            }
        }

        @Test
        @DisplayName("같은 userId는 항상 같은 칸에 걸린다(박싱 캐시 밖의 서로 다른 Long 객체여도)")
        void sameUserSameStripe() {
            for (long id = -500; id <= 500; id++) {
                Long boxed = Long.valueOf(id);
                Long parsed = Long.valueOf(Long.toString(id));
                assertThat(AdminRequestCommandService.approvalLockStripe(boxed))
                        .isEqualTo(AdminRequestCommandService.approvalLockStripe(parsed));
            }
        }

        @Test
        @DisplayName("연속된 userId는 칸 수만큼 서로 다른 칸에 고르게 퍼진다")
        void consecutiveIdsSpreadAcrossAllStripes() {
            java.util.Set<Integer> stripes = new java.util.HashSet<>();
            for (long id = 1000; id < 1000 + AdminRequestCommandService.APPROVAL_LOCK_STRIPES; id++) {
                stripes.add(AdminRequestCommandService.approvalLockStripe(id));
            }
            assertThat(stripes).hasSize(AdminRequestCommandService.APPROVAL_LOCK_STRIPES);
        }

        @Test
        @DisplayName("잠금 개수는 고정이다 — 승인한 사용자 수만큼 늘지 않는다")
        void lockCountDoesNotGrowWithUsers() {
            java.util.concurrent.atomic.AtomicLong jobIds = new java.util.concurrent.atomic.AtomicLong(1L);
            when(jobClient.registerProvision(any())).thenAnswer(inv -> jobIds.incrementAndGet());
            Object[] before = (Object[]) ReflectionTestUtils.getField(service, "approvalLocks");

            for (long userId = 10_000; userId < 10_200; userId++) {
                Long requestId = 50_000 + userId;
                pendingRequestOf(requestId, userWithId(userId));
                service.approveRequest(new ApproveRequestDTO(requestId, 1L, 1, null));
            }

            Object[] after = (Object[]) ReflectionTestUtils.getField(service, "approvalLocks");
            assertThat(after).isSameAs(before).hasSize(AdminRequestCommandService.APPROVAL_LOCK_STRIPES);
            assertThat(java.util.Arrays.stream(after).distinct().count())
                    .isEqualTo(AdminRequestCommandService.APPROVAL_LOCK_STRIPES);
            verify(jobClient, times(200)).registerProvision(any());
        }

        @Test
        @DisplayName("같은 사용자의 두 승인은 앞선 등록이 끝날 때까지 뒤쪽이 기다린다")
        void sameUserIsSerialized() throws Exception {
            User owner = userWithId(100L);
            pendingRequestOf(301L, owner);
            pendingRequestOf(302L, owner);
            var latches = holdRegistrationOf(301L);
            List<Throwable> failures = java.util.Collections.synchronizedList(new java.util.ArrayList<>());

            Thread first = approveAsync(301L, failures);
            assertThat(latches[0].await(WAIT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            Thread second = approveAsync(302L, failures);
            awaitBlocked(second);

            // 앞선 등록이 잠금을 쥔 동안 뒤쪽은 PROCESSING 조회조차 하지 못한다.
            verify(jobClient, times(1)).registerProvision(any());
            verify(requestRepository, times(1)).findAllByUser_UserIdAndStatus(100L, Status.PROCESSING);

            latches[1].countDown();
            joinAll(first, second);
            assertThat(failures).isEmpty();
            verify(jobClient, times(2)).registerProvision(any());
        }

        @Test
        @DisplayName("다른 칸에 걸린 다른 사용자는 앞선 등록이 멈춰 있어도 기다리지 않는다")
        void differentStripeRunsInParallel() throws Exception {
            assertThat(AdminRequestCommandService.approvalLockStripe(100L))
                    .isNotEqualTo(AdminRequestCommandService.approvalLockStripe(101L));
            pendingRequestOf(311L, userWithId(100L));
            pendingRequestOf(312L, userWithId(101L));
            var latches = holdRegistrationOf(311L);
            List<Throwable> failures = java.util.Collections.synchronizedList(new java.util.ArrayList<>());

            Thread first = approveAsync(311L, failures);
            assertThat(latches[0].await(WAIT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            Thread other = approveAsync(312L, failures);
            joinAll(other); // 앞선 스레드는 아직 등록 안에서 멈춰 있다

            assertThat(first.isAlive()).isTrue();
            latches[1].countDown();
            joinAll(first);
            assertThat(failures).isEmpty();
            verify(jobClient, times(2)).registerProvision(any());
        }

        @Test
        @DisplayName("같은 칸을 나눠 쓰는 다른 사용자는 잠깐 기다린 뒤 정상 승인된다 — 막히거나 거절되지 않는다")
        void sharedStripeWaitsThenSucceeds() throws Exception {
            long a = 100L;
            long b = a + AdminRequestCommandService.APPROVAL_LOCK_STRIPES;
            assertThat(AdminRequestCommandService.approvalLockStripe(a))
                    .isEqualTo(AdminRequestCommandService.approvalLockStripe(b));
            pendingRequestOf(321L, userWithId(a));
            pendingRequestOf(322L, userWithId(b));
            var latches = holdRegistrationOf(321L);
            List<Throwable> failures = java.util.Collections.synchronizedList(new java.util.ArrayList<>());

            Thread first = approveAsync(321L, failures);
            assertThat(latches[0].await(WAIT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            Thread second = approveAsync(322L, failures);
            awaitBlocked(second);
            verify(jobClient, times(1)).registerProvision(any());

            latches[1].countDown();
            joinAll(first, second);
            assertThat(failures).isEmpty();
            verify(jobClient, times(2)).registerProvision(any());
            // 다른 사용자라 서로를 "처리 중인 같은 사용자 신청"으로 보지 않는다.
            verify(requestRepository).findAllByUser_UserIdAndStatus(a, Status.PROCESSING);
            verify(requestRepository).findAllByUser_UserIdAndStatus(b, Status.PROCESSING);
        }

        @Test
        @DisplayName("등록이 예외로 끝나도 잠금이 풀려 같은 사용자의 다음 승인이 진행된다")
        void lockReleasedAfterRegistrationFailure() {
            User owner = userWithId(100L);
            pendingRequestOf(331L, owner);
            pendingRequestOf(332L, owner);
            when(jobClient.registerProvision(any()))
                    .thenThrow(new BusinessException(ErrorCode.POD_CREATION_FAILED))
                    .thenReturn(7332L);

            assertThatThrownBy(() -> service.approveRequest(new ApproveRequestDTO(331L, 1L, 1, null)))
                    .isInstanceOf(BusinessException.class);
            assertThat(service.approveRequest(new ApproveRequestDTO(332L, 1L, 1, null))).isNotNull();
            verify(jobClient, times(2)).registerProvision(any());
        }

        @Test
        @DisplayName("다른 처리 중 신청 때문에 거절돼도 잠금이 풀려, 막던 신청이 끝난 뒤 다시 승인할 수 있다")
        void lockReleasedAfterBlockedRejection() {
            User owner = userWithId(100L);
            pendingRequestOf(341L, owner);
            Request blocking = mock(Request.class);
            when(blocking.getRequestId()).thenReturn(340L);
            when(requestRepository.findAllByUser_UserIdAndStatus(100L, Status.PROCESSING))
                    .thenReturn(List.of(blocking))
                    .thenReturn(List.of());
            when(jobClient.registerProvision(any())).thenReturn(7341L);

            assertThatThrownBy(() -> service.approveRequest(new ApproveRequestDTO(341L, 1L, 1, null)))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_APPROVAL_ALREADY_IN_PROGRESS);

            pendingRequestOf(341L, owner); // PENDING으로 되돌아간 뒤 관리자가 다시 승인
            assertThat(service.approveRequest(new ApproveRequestDTO(341L, 1L, 1, null))).isNotNull();
            verify(jobClient, times(1)).registerProvision(any());
        }
    }
}
