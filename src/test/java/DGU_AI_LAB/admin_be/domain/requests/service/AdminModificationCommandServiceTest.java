package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.domain.requests.job.JobClient;
import DGU_AI_LAB.admin_be.domain.alarm.service.AlarmService;
import DGU_AI_LAB.admin_be.domain.containerImage.entity.ContainerImage;
import DGU_AI_LAB.admin_be.domain.groups.entity.Group;
import DGU_AI_LAB.admin_be.domain.groups.service.GroupOperationService;
import DGU_AI_LAB.admin_be.domain.pod.entity.PodExternalPort;
import DGU_AI_LAB.admin_be.domain.pod.repository.PodExternalPortRepository;
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
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
    @Mock private ChangeRequestRepository changeRequestRepository;
    @Mock private GroupOperationService groupOperationService;
    @Mock private PodExternalPortRepository podExternalPortRepository;
    @Mock private JobClient jobClient;
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
                alarmService, requestRepository, userRepository, changeRequestRepository,
                groupOperationService, new ObjectMapper()
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
            LocalDateTime newExpiry = LocalDateTime.now().plusYears(1).withNano(0);

            ChangeRequest changeRequest = mock(ChangeRequest.class);
            Request originalRequest = buildMockedRequestWithStatus(21L, Status.FULFILLED);
            when(originalRequest.getExpiresAt()).thenReturn(oldExpiry);
            when(changeRequest.getStatus()).thenReturn(Status.PENDING);
            when(changeRequest.getChangeType()).thenReturn(ChangeType.EXPIRES_AT);
            when(changeRequest.getNewValue()).thenReturn("\"" + newExpiry + "\"");
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

        @ParameterizedTest
        @EnumSource(value = ChangeType.class, names = {"RESOURCE_GROUP", "CONTAINER_IMAGE", "PORT"})
        @DisplayName("DB만 바뀌고 Pod에 반영되지 않는 종류는 예전에 들어온 요청이라도 승인하지 않고 신청을 건드리지 않는다")
        void approveModification_dbOnlyTypes_areUnsupported(ChangeType type) {
            ChangeRequest changeRequest = mock(ChangeRequest.class);
            Request originalRequest = buildMockedRequestWithStatus(22L, Status.FULFILLED);
            when(changeRequest.getStatus()).thenReturn(Status.PENDING);
            when(changeRequest.getChangeType()).thenReturn(type);
            when(changeRequest.getNewValue()).thenReturn("2");
            when(changeRequest.getRequest()).thenReturn(originalRequest);
            when(changeRequestRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(changeRequest));
            when(userRepository.findById(100L)).thenReturn(Optional.of(mockUser));

            assertThatThrownBy(() -> service.approveModification(100L, new ApproveModificationDTO(3L, "승인")))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.UNSUPPORTED_CHANGE_TYPE);

            verify(originalRequest, never()).updateResourceGroup(any());
            verify(originalRequest, never()).updateContainerImage(any());
            verify(changeRequest, never()).approve(any(), anyString());
            verify(alarmService, never()).sendModificationApprovedEmail(any(), any());
        }

        @Test
        @DisplayName("GROUP 변경 요청 승인은 반영 작업 등록으로 넘기고, 승인 완료·안내 메일은 작업이 끝난 뒤로 미룬다")
        void approveModification_group_registersJobInsteadOfApplying() {
            ChangeRequest changeRequest = mock(ChangeRequest.class);
            Request originalRequest = buildMockedRequestWithStatus(25L, Status.FULFILLED);
            when(changeRequest.getStatus()).thenReturn(Status.PENDING);
            when(changeRequest.getChangeType()).thenReturn(ChangeType.GROUP);
            when(changeRequest.getRequest()).thenReturn(originalRequest);
            when(changeRequestRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(changeRequest));
            when(userRepository.findById(100L)).thenReturn(Optional.of(mockUser));

            service.approveModification(100L, new ApproveModificationDTO(9L, "그룹 변경 승인"));

            verify(groupOperationService).startAdd(changeRequest, originalRequest, mockUser, "그룹 변경 승인");
            // 작업이 성공하기 전에는 승인된 것이 아니다 — DB 기록과 안내는 GroupOperationService.complete 가 한다.
            verify(changeRequest, never()).approve(any(), any());
            verify(mockUser, never()).addGroupIfAbsent(any());
            verify(alarmService, never()).sendGroupAddedEmail(any(), any(), anyList());
            verify(alarmService, never()).sendModificationApprovedEmail(any(), any());
        }

        @Test
        @DisplayName("GROUP 변경 승인 중 작업 등록이 실패하면 예외가 그대로 전파되고 승인 처리는 하지 않는다")
        void approveModification_group_registrationFailurePropagates() {
            ChangeRequest changeRequest = mock(ChangeRequest.class);
            Request originalRequest = buildMockedRequestWithStatus(27L, Status.FULFILLED);
            when(changeRequest.getStatus()).thenReturn(Status.PENDING);
            when(changeRequest.getChangeType()).thenReturn(ChangeType.GROUP);
            when(changeRequest.getRequest()).thenReturn(originalRequest);
            when(changeRequestRepository.findByIdForUpdate(16L)).thenReturn(Optional.of(changeRequest));
            when(userRepository.findById(100L)).thenReturn(Optional.of(mockUser));
            doThrow(new BusinessException(ErrorCode.GROUP_CHANGE_FAILED))
                    .when(groupOperationService).startAdd(any(), any(), any(), any());

            assertThatThrownBy(() -> service.approveModification(100L, new ApproveModificationDTO(16L, "승인")))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.GROUP_CHANGE_FAILED);

            verify(changeRequest, never()).approve(any(), any());
            verify(alarmService, never()).sendGroupAddedEmail(any(), any(), anyList());
        }

        @Test
        @DisplayName("요청한 만료 일시가 승인 시점에 이미 지났으면 반영하지 않는다 — 반영하면 연장이 즉시 삭제로 바뀐다")
        void approveModification_expiresAtAlreadyPassed_throwsAndDoesNotApply() {
            ChangeRequest changeRequest = mock(ChangeRequest.class);
            Request originalRequest = buildMockedRequestWithStatus(27L, Status.FULFILLED);
            when(changeRequest.getStatus()).thenReturn(Status.PENDING);
            when(changeRequest.getChangeType()).thenReturn(ChangeType.EXPIRES_AT);
            when(changeRequest.getNewValue()).thenReturn("\"" + LocalDateTime.now().minusDays(1).withNano(0) + "\"");
            when(changeRequest.getRequest()).thenReturn(originalRequest);
            when(changeRequestRepository.findByIdForUpdate(16L)).thenReturn(Optional.of(changeRequest));
            when(userRepository.findById(100L)).thenReturn(Optional.of(mockUser));

            assertThatThrownBy(() -> service.approveModification(100L, new ApproveModificationDTO(16L, "승인")))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.CHANGE_REQUEST_EXPIRES_AT_PASSED);

            verify(originalRequest, never()).updateExpiresAt(any());
            verify(changeRequest, never()).approve(any(), any());
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
