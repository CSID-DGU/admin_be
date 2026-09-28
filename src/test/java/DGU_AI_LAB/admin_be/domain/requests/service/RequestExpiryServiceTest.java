package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.domain.requests.job.JobClient;
import DGU_AI_LAB.admin_be.domain.requests.job.JobResults;
import DGU_AI_LAB.admin_be.domain.pod.repository.PodExternalPortRepository;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.RevokeRegisterRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.JobResultResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import DGU_AI_LAB.admin_be.error.exception.EntityNotFoundException;
import DGU_AI_LAB.admin_be.global.event.RequestContainerDeletedEvent;
import DGU_AI_LAB.admin_be.global.event.RequestExpiredEvent;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 컨테이너 회수는 작업을 등록하고 바로 돌아온다(FULFILLED → EXPIRING). 결과 반영(DELETED 또는 FULFILLED로
 * 되돌림)은 결과 폴러가 completeContainerRevoke / failContainerRevoke로 한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RequestExpiryService")
class RequestExpiryServiceTest {

    private static final String POD_NAME = "pod-testuser-xxxx";

    @Mock private RequestRepository requestRepository;
    @Mock private JobClient jobClient;
    @Mock private PodExternalPortRepository podExternalPortRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private PlatformTransactionManager transactionManager;
    @Mock private TransactionStatus transactionStatus;

    @Mock private ResourceGroup mockRg;
    @Mock private User mockUser;

    private RequestExpiryService service;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        service = new RequestExpiryService(
                requestRepository, jobClient, podExternalPortRepository, eventPublisher, transactionManager);
        when(mockRg.getServerName()).thenReturn("FARM-01");
        when(mockUser.getName()).thenReturn("테스트유저");
        when(mockUser.getEmail()).thenReturn("test@dgu.ac.kr");
        when(jobClient.registerRevoke(any(), any())).thenReturn(900L);
    }

    /** 상태가 고정된 mock으로는 선점 후 재확인하는 흐름이 재현되지 않아 상태 전이를 흉내낸다. */
    private Request mockRequest(Long requestId, Status status, LocalDateTime expiresAt) {
        Request request = mock(Request.class);
        AtomicReference<Status> current = new AtomicReference<>(status);
        when(request.getRequestId()).thenReturn(requestId);
        when(request.getStatus()).thenAnswer(inv -> current.get());
        doAnswer(inv -> { current.set(Status.EXPIRING); return null; }).when(request).beginExpiry();
        doAnswer(inv -> { current.set(Status.FULFILLED); return null; }).when(request).endExpiry();
        doAnswer(inv -> { current.set(Status.DELETED); return null; }).when(request).deleteAfterCleanup();
        when(request.getUbuntuUsername()).thenReturn("testuser");
        when(request.getUser()).thenReturn(mockUser);
        when(request.getResourceGroup()).thenReturn(mockRg);
        when(request.getPodName()).thenReturn(POD_NAME);
        when(request.getExpiresAt()).thenReturn(expiresAt);
        // Mockito는 Long getter에 0L을 돌려준다 — 실제 엔티티처럼 "작업 번호 없음"은 null이어야 한다.
        when(request.getJobId()).thenReturn(null);
        when(requestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));
        when(podExternalPortRepository.findByRequestRequestId(requestId)).thenReturn(List.of());
        return request;
    }

    private Request mockRequest(Long requestId, Status status) {
        return mockRequest(requestId, status, LocalDateTime.of(2026, 1, 1, 0, 0));
    }

    private static JobResultResponseDTO revokeJob(String phase, Long jobId) {
        return new JobResultResponseDTO("1", JobResults.KIND_REVOKE, jobId, phase, null, null, null);
    }

    @Nested
    @DisplayName("회수 시작")
    class Start {

        @Test
        @DisplayName("FULFILLED면 EXPIRING으로 선점하고 Pod 회수 작업을 등록한 뒤 작업 번호를 남긴다 — 계정은 회수하지 않는다")
        void registersPodOnlyRevokeAndRecordsJob() {
            Request request = mockRequest(1L, Status.FULFILLED);

            service.deleteExpiredRequest(1L);

            ArgumentCaptor<RevokeRegisterRequestDTO> body = ArgumentCaptor.forClass(RevokeRegisterRequestDTO.class);
            verify(jobClient).registerRevoke(body.capture(), eq(ErrorCode.POD_DELETION_FAILED));
            assertThat(body.getValue().requestId()).isEqualTo(1L);
            assertThat(body.getValue().podName()).isEqualTo(POD_NAME);
            assertThat(body.getValue().deleteAccount()).isFalse();
            assertThat(body.getValue().username()).isNull();
            verify(request).recordJob(900L);
            assertThat(request.getStatus()).isEqualTo(Status.EXPIRING);
            // 결과를 기다리지 않으므로 DELETED 전환도 안내도 아직 없다.
            verify(request, never()).deleteAfterCleanup();
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("FULFILLED가 아니면 만료 경로는 조용히 넘어간다")
        void expirySkipsNonFulfilled() {
            for (Status status : List.of(Status.PENDING, Status.PROCESSING, Status.EXPIRING, Status.DELETED)) {
                Request request = mockRequest(2L, status);

                service.deleteExpiredRequest(2L);

                verify(request, never()).beginExpiry();
            }
            verify(jobClient, never()).registerRevoke(any(), any());
        }

        @Test
        @DisplayName("관리자 경로는 FULFILLED가 아니면 409로 알린다 — 누른 버튼은 결과를 돌려줘야 한다")
        void adminRejectsNonFulfilled() {
            mockRequest(3L, Status.EXPIRING);

            assertThatThrownBy(() -> service.deleteContainerByAdmin(3L))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_REQUEST_STATUS);
            verify(jobClient, never()).registerRevoke(any(), any());
        }

        @Test
        @DisplayName("신청이 없으면 EntityNotFoundException을 던진다")
        void missingRequest() {
            when(requestRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.deleteExpiredRequest(99L)).isInstanceOf(EntityNotFoundException.class);
        }

        @Test
        @DisplayName("등록이 실패했고 도는 작업도 없으면 FULFILLED로 되돌리고 예외를 올린다 — 다음 만료 회차가 다시 잡는다")
        void registrationFailureReverts() {
            Request request = mockRequest(4L, Status.FULFILLED);
            when(jobClient.registerRevoke(any(), any()))
                    .thenThrow(new BusinessException("작업 등록 실패", ErrorCode.POD_DELETION_FAILED));
            when(jobClient.getResult(JobResults.KIND_REVOKE, 4L))
                    .thenReturn(revokeJob(JobResults.PHASE_NONE, null));

            assertThatThrownBy(() -> service.deleteExpiredRequest(4L))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POD_DELETION_FAILED);

            assertThat(request.getStatus()).isEqualTo(Status.FULFILLED);
            verify(request, never()).recordJob(any());
        }

        @Test
        @DisplayName("등록 응답은 실패했지만 같은 신청의 회수 작업이 도는 중이면 그 작업을 이어받는다")
        void registrationFailureWithRunningJobAdoptsIt() {
            Request request = mockRequest(5L, Status.FULFILLED);
            when(jobClient.registerRevoke(any(), any()))
                    .thenThrow(new BusinessException("이미 처리 중", ErrorCode.INVALID_REQUEST_STATUS));
            when(jobClient.getResult(JobResults.KIND_REVOKE, 5L))
                    .thenReturn(revokeJob(JobResults.PHASE_START, 812L));

            service.deleteContainerByAdmin(5L);

            assertThat(request.getStatus()).isEqualTo(Status.EXPIRING);
            verify(request).recordJob(812L);
        }

        @Test
        @DisplayName("등록 응답은 실패했지만 회수 작업이 단계 재시도(RETRY) 중이면 이어받는다 — START만 보면 놓친다")
        void registrationFailureWithRetryingJobAdoptsIt() {
            Request request = mockRequest(8L, Status.FULFILLED);
            when(jobClient.registerRevoke(any(), any()))
                    .thenThrow(new BusinessException("이미 처리 중", ErrorCode.INVALID_REQUEST_STATUS));
            when(jobClient.getResult(JobResults.KIND_REVOKE, 8L))
                    .thenReturn(revokeJob(JobResults.PHASE_RETRY, 813L));

            service.deleteContainerByAdmin(8L);

            assertThat(request.getStatus()).isEqualTo(Status.EXPIRING);
            verify(request).recordJob(813L);
        }

        @Test
        @DisplayName("config-server에 연결조차 못 했으면 조회 없이 바로 FULFILLED로 되돌린다 — 작업이 없는 것이 확실하다")
        void unreachableServerRevertsImmediately() {
            Request request = mockRequest(7L, Status.FULFILLED);
            when(jobClient.registerRevoke(any(), any())).thenThrow(new BusinessException(
                    "작업 등록 중 오류", ErrorCode.POD_DELETION_FAILED,
                    new RuntimeException(new java.net.ConnectException("Connection refused"))));

            assertThatThrownBy(() -> service.deleteContainerByAdmin(7L))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POD_DELETION_FAILED);
            assertThat(request.getStatus()).isEqualTo(Status.FULFILLED);
            verify(jobClient, never()).getResult(any(), anyLong());
        }

        @Test
        @DisplayName("등록 실패 후 작업 상태도 모르면 EXPIRING으로 두고 예외를 올린다 — 재조정이 작업 상태를 보고 판단한다")
        void registrationAndLookupFailureKeepsExpiring() {
            Request request = mockRequest(6L, Status.FULFILLED);
            when(jobClient.registerRevoke(any(), any())).thenThrow(new RuntimeException("timeout"));
            when(jobClient.getResult(any(), anyLong())).thenThrow(new RuntimeException("timeout"));

            assertThatThrownBy(() -> service.deleteExpiredRequest(6L))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POD_DELETION_FAILED);
            assertThat(request.getStatus()).isEqualTo(Status.EXPIRING);
        }
    }

    @Nested
    @DisplayName("회수 결과 반영")
    class Complete {

        @Test
        @DisplayName("성공하면 DELETED로 바꾸고 외부 포트를 회수한 뒤, 만료일이 지난 신청이면 만료 안내를 발행한다")
        void successOnExpiredRequestPublishesExpiredEvent() {
            Request request = mockRequest(10L, Status.EXPIRING, LocalDateTime.now().minusDays(1));

            service.completeContainerRevoke(10L);

            verify(request).deleteAfterCleanup();
            verify(podExternalPortRepository).deleteByRequestRequestId(10L);
            ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
            verify(eventPublisher).publishEvent(event.capture());
            assertThat(event.getValue()).isInstanceOf(RequestExpiredEvent.class);
            assertThat(((RequestExpiredEvent) event.getValue()).ubuntuUsername()).isEqualTo("testuser");
        }

        @Test
        @DisplayName("만료일이 남은 신청(관리자 회수·사용자 정리)이면 회수 안내를 발행한다")
        void successOnLiveRequestPublishesContainerDeletedEvent() {
            mockRequest(11L, Status.EXPIRING, LocalDateTime.now().plusDays(30));

            service.completeContainerRevoke(11L);

            ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
            verify(eventPublisher).publishEvent(event.capture());
            assertThat(event.getValue()).isInstanceOf(RequestContainerDeletedEvent.class);
        }

        @Test
        @DisplayName("그 사이 상태가 바뀌었으면 덮어쓰지 않는다")
        void notExpiringIsIgnored() {
            Request request = mockRequest(12L, Status.FULFILLED);

            service.completeContainerRevoke(12L);

            verify(request, never()).deleteAfterCleanup();
            verify(podExternalPortRepository, never()).deleteByRequestRequestId(any());
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("실패하면 FULFILLED로 되돌린다 — 다음 만료 회차나 관리자의 재시도가 다시 회수한다")
        void failureReverts() {
            Request request = mockRequest(13L, Status.EXPIRING);

            service.failContainerRevoke(13L);

            assertThat(request.getStatus()).isEqualTo(Status.FULFILLED);
            verify(request, never()).deleteAfterCleanup();
        }

        @Test
        @DisplayName("방치된 EXPIRING은 FULFILLED로 되돌리고, EXPIRING이 아니면 건드리지 않는다")
        void revertStaleExpiring() {
            Request stale = mockRequest(14L, Status.EXPIRING);
            service.revertStaleExpiring(14L);
            assertThat(stale.getStatus()).isEqualTo(Status.FULFILLED);

            Request deleted = mockRequest(15L, Status.DELETED);
            service.revertStaleExpiring(15L);
            verify(deleted, never()).endExpiry();
        }
    }

    @Nested
    @DisplayName("강제 완료")
    class ForceComplete {

        @Test
        @DisplayName("DEGRADED로 멈춘 EXPIRING은 강제 완료 처리된다 — 정상 완료와 같은 정리(포트 회수·안내)를 거친다")
        void degradedIsForceCompleted() {
            Request request = mockRequest(16L, Status.EXPIRING);
            when(jobClient.getResult(JobResults.KIND_REVOKE, 16L)).thenReturn(new JobResultResponseDTO(
                    "16", JobResults.KIND_REVOKE, 900L, JobResults.PHASE_FAIL, JobResults.ERROR_DEGRADED, null, null));

            service.forceCompleteRevoke(16L);

            assertThat(request.getStatus()).isEqualTo(Status.DELETED);
            verify(podExternalPortRepository).deleteByRequestRequestId(16L);
            // publishEvent가 Object/ApplicationEvent로 오버로드돼 있어 타입 없는 any()는 특정 오버로드에만
            // 묶인다 — Object로 캡처해야 실제 호출(비-ApplicationEvent 이벤트)을 잡는다(파일의 다른 시험과 동일).
            ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
            verify(eventPublisher).publishEvent(event.capture());
        }

        @Test
        @DisplayName("UNKNOWN(결과 불명)으로 멈춘 EXPIRING도 강제 완료 처리된다")
        void unknownIsForceCompleted() {
            Request request = mockRequest(17L, Status.EXPIRING);
            when(jobClient.getResult(JobResults.KIND_REVOKE, 17L)).thenReturn(new JobResultResponseDTO(
                    "17", JobResults.KIND_REVOKE, 900L, JobResults.PHASE_UNKNOWN, "TIMEOUT", null, null));

            service.forceCompleteRevoke(17L);

            assertThat(request.getStatus()).isEqualTo(Status.DELETED);
        }

        @Test
        @DisplayName("EXPIRING이 아니면 409 — 정상 사용 중인 컨테이너까지 강제로 지우면 안 된다")
        void rejectsNonExpiring() {
            mockRequest(18L, Status.FULFILLED);

            assertThatThrownBy(() -> service.forceCompleteRevoke(18L))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_REQUEST_STATUS);
            verify(jobClient, never()).getResult(any(), any());
        }

        @Test
        @DisplayName("EXPIRING이어도 회수 결과가 평범한 FAIL(자원을 안 남긴 실패)이면 거부한다 — 자동으로 되돌아갈 여지가 있다")
        void rejectsPlainFail() {
            Request request = mockRequest(19L, Status.EXPIRING);
            when(jobClient.getResult(JobResults.KIND_REVOKE, 19L)).thenReturn(new JobResultResponseDTO(
                    "19", JobResults.KIND_REVOKE, 900L, JobResults.PHASE_FAIL, "POD_DELETE_FAILED", null, null));

            assertThatThrownBy(() -> service.forceCompleteRevoke(19L))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_REQUEST_STATUS);
            verify(request, never()).deleteAfterCleanup();
        }

        @Test
        @DisplayName("EXPIRING이어도 아직 실행 중(START·RETRY)이면 거부한다")
        void rejectsStillRunning() {
            Request request = mockRequest(20L, Status.EXPIRING);
            when(jobClient.getResult(JobResults.KIND_REVOKE, 20L)).thenReturn(revokeJob(JobResults.PHASE_RETRY, 900L));

            assertThatThrownBy(() -> service.forceCompleteRevoke(20L))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_REQUEST_STATUS);
            verify(request, never()).deleteAfterCleanup();
        }

        @Test
        @DisplayName("신청이 없으면 EntityNotFoundException")
        void missing() {
            when(requestRepository.findByIdForUpdate(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.forceCompleteRevoke(999L)).isInstanceOf(EntityNotFoundException.class);
        }
        private JobResultResponseDTO revokeResult(Long jobId, String phase, String errorCode) {
            return new JobResultResponseDTO("1", JobResults.KIND_REVOKE, jobId, phase, errorCode, null, null);
        }

        private JobResultResponseDTO degraded(Long jobId) {
            return revokeResult(jobId, JobResults.PHASE_FAIL, JobResults.ERROR_DEGRADED);
        }

        private void assertRejectedUntouched(Long requestId, Request request) {
            assertThatThrownBy(() -> service.forceCompleteRevoke(requestId))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_REQUEST_STATUS);
            assertThat(request.getStatus()).isEqualTo(Status.EXPIRING);
            verify(request, never()).deleteAfterCleanup();
            verify(podExternalPortRepository, never()).deleteByRequestRequestId(anyLong());
            verify(eventPublisher, never()).publishEvent(any(Object.class));
        }

        @Test
        @DisplayName("기록된 작업 번호와 결과의 작업 번호가 같으면 강제 완료한다")
        void matchingJobIsForceCompleted() {
            Request request = mockRequest(30L, Status.EXPIRING);
            when(request.getJobId()).thenReturn(910L);
            when(jobClient.getResult(JobResults.KIND_REVOKE, 30L)).thenReturn(degraded(910L));

            service.forceCompleteRevoke(30L);

            assertThat(request.getStatus()).isEqualTo(Status.DELETED);
            verify(podExternalPortRepository).deleteByRequestRequestId(30L);
        }

        @Test
        @DisplayName("결과가 이전 회수 작업의 것이면(번호 다름) 거부한다 — 지금 도는 회수를 건너뛰고 DELETED로 끝내면 안 된다")
        void rejectsResultOfOtherJob() {
            Request request = mockRequest(31L, Status.EXPIRING);
            when(request.getJobId()).thenReturn(920L);
            when(jobClient.getResult(JobResults.KIND_REVOKE, 31L)).thenReturn(degraded(919L));

            assertRejectedUntouched(31L, request);
        }

        @Test
        @DisplayName("이전 작업의 결과가 UNKNOWN이어도 번호가 다르면 거부한다")
        void rejectsUnknownOfOtherJob() {
            Request request = mockRequest(32L, Status.EXPIRING);
            when(request.getJobId()).thenReturn(930L);
            when(jobClient.getResult(JobResults.KIND_REVOKE, 32L))
                    .thenReturn(revokeResult(929L, JobResults.PHASE_UNKNOWN, "TIMEOUT"));

            assertRejectedUntouched(32L, request);
        }

        @Test
        @DisplayName("회수를 막 시작해 작업 번호가 아직 없으면(등록 대기) 결과를 조회하지 않고 거부한다 — 보이는 결과는 이전 작업의 것이다")
        void rejectsWhileAwaitingRegistration() {
            Request request = mockRequest(33L, Status.EXPIRING);
            when(request.getJobId()).thenReturn(null);
            when(request.getUpdatedAt()).thenReturn(LocalDateTime.now().minusSeconds(5));
            when(jobClient.getResult(JobResults.KIND_REVOKE, 33L)).thenReturn(degraded(800L));

            assertRejectedUntouched(33L, request);
            verify(jobClient, never()).getResult(any(), any());
        }

        @Test
        @DisplayName("작업 번호가 끝내 기록되지 못했어도 등록 유예가 지났으면 결과 폴러와 같이 보이는 결과를 받아들인다")
        void acceptsResultWhenJobIdNeverRecordedAfterGrace() {
            Request request = mockRequest(34L, Status.EXPIRING);
            when(request.getJobId()).thenReturn(null);
            when(request.getUpdatedAt()).thenReturn(
                    LocalDateTime.now().minus(JobResults.REGISTRATION_GRACE).minusMinutes(1));
            when(jobClient.getResult(JobResults.KIND_REVOKE, 34L)).thenReturn(degraded(940L));

            service.forceCompleteRevoke(34L);

            assertThat(request.getStatus()).isEqualTo(Status.DELETED);
        }

        @Test
        @DisplayName("작업 번호가 기록됐으면 방금 상태가 바뀌었어도 등록 대기로 보지 않는다")
        void recordedJobIsNotAwaitingRegistration() {
            Request request = mockRequest(35L, Status.EXPIRING);
            when(request.getJobId()).thenReturn(950L);
            when(request.getUpdatedAt()).thenReturn(LocalDateTime.now());
            when(jobClient.getResult(JobResults.KIND_REVOKE, 35L)).thenReturn(degraded(950L));

            service.forceCompleteRevoke(35L);

            assertThat(request.getStatus()).isEqualTo(Status.DELETED);
        }

        @Test
        @DisplayName("결과에 작업 번호가 없으면(구버전 응답) 다른 작업으로 판정하지 않는다 — 결과 폴러와 같은 기준")
        void resultWithoutJobIdIsNotOtherJob() {
            Request request = mockRequest(36L, Status.EXPIRING);
            when(request.getJobId()).thenReturn(960L);
            when(jobClient.getResult(JobResults.KIND_REVOKE, 36L)).thenReturn(degraded(null));

            service.forceCompleteRevoke(36L);

            assertThat(request.getStatus()).isEqualTo(Status.DELETED);
        }

        @Test
        @DisplayName("결과 조회가 null을 돌려주면 NPE 대신 409로 거부한다")
        void rejectsNullResult() {
            Request request = mockRequest(37L, Status.EXPIRING);
            when(jobClient.getResult(JobResults.KIND_REVOKE, 37L)).thenReturn(null);

            assertRejectedUntouched(37L, request);
        }

        @Test
        @DisplayName("결과 조회가 실패하면 예외를 그대로 올리고 신청은 건드리지 않는다")
        void propagatesLookupFailure() {
            Request request = mockRequest(38L, Status.EXPIRING);
            when(jobClient.getResult(JobResults.KIND_REVOKE, 38L)).thenThrow(new IllegalStateException("config-server down"));

            assertThatThrownBy(() -> service.forceCompleteRevoke(38L)).isInstanceOf(IllegalStateException.class);
            assertThat(request.getStatus()).isEqualTo(Status.EXPIRING);
            verify(request, never()).deleteAfterCleanup();
        }

        @Test
        @DisplayName("회수가 성공(SUCCESS)했으면 거부한다 — 결과 폴러가 정상 경로로 완료한다")
        void rejectsSuccess() {
            Request request = mockRequest(39L, Status.EXPIRING);
            when(jobClient.getResult(JobResults.KIND_REVOKE, 39L))
                    .thenReturn(revokeResult(990L, JobResults.PHASE_SUCCESS, null));

            assertRejectedUntouched(39L, request);
        }

        @Test
        @DisplayName("등록 이력이 없으면(none) 거부한다 — 재조정 스케줄러가 FULFILLED로 되돌린다")
        void rejectsNone() {
            Request request = mockRequest(40L, Status.EXPIRING);
            when(jobClient.getResult(JobResults.KIND_REVOKE, 40L))
                    .thenReturn(revokeResult(null, JobResults.PHASE_NONE, null));

            assertRejectedUntouched(40L, request);
        }

        @Test
        @DisplayName("START로 실행 중이면 거부한다")
        void rejectsStart() {
            Request request = mockRequest(41L, Status.EXPIRING);
            when(jobClient.getResult(JobResults.KIND_REVOKE, 41L)).thenReturn(revokeJob(JobResults.PHASE_START, 410L));

            assertRejectedUntouched(41L, request);
        }

        @Test
        @DisplayName("상태 확인 뒤 결과 조회 사이에 다른 경로가 FULFILLED로 되돌렸으면 완료 처리하지 않는다")
        void skipsWhenStateChangedDuringLookup() {
            Request request = mockRequest(42L, Status.EXPIRING);
            when(jobClient.getResult(JobResults.KIND_REVOKE, 42L)).thenAnswer(inv -> {
                request.endExpiry();
                return degraded(420L);
            });

            service.forceCompleteRevoke(42L);

            assertThat(request.getStatus()).isEqualTo(Status.FULFILLED);
            verify(request, never()).deleteAfterCleanup();
            verify(podExternalPortRepository, never()).deleteByRequestRequestId(anyLong());
            verify(eventPublisher, never()).publishEvent(any(Object.class));
        }

        @Test
        @DisplayName("만료일이 지난 신청을 강제 완료하면 만료 안내를 보낸다")
        void expiredSendsExpiredEvent() {
            mockRequest(43L, Status.EXPIRING, LocalDateTime.now().minusDays(1));
            when(jobClient.getResult(JobResults.KIND_REVOKE, 43L)).thenReturn(degraded(430L));

            service.forceCompleteRevoke(43L);

            ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
            verify(eventPublisher).publishEvent(event.capture());
            assertThat(event.getValue()).isInstanceOf(RequestExpiredEvent.class);
        }

        @Test
        @DisplayName("만료일 전 신청을 강제 완료하면 회수 안내를 보낸다")
        void notExpiredSendsDeletedEvent() {
            mockRequest(44L, Status.EXPIRING, LocalDateTime.now().plusDays(30));
            when(jobClient.getResult(JobResults.KIND_REVOKE, 44L)).thenReturn(degraded(440L));

            service.forceCompleteRevoke(44L);

            ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
            verify(eventPublisher).publishEvent(event.capture());
            assertThat(event.getValue()).isInstanceOf(RequestContainerDeletedEvent.class);
        }

        @Test
        @DisplayName("EXPIRING이 아니면 상태마다 결과 조회 없이 409")
        void rejectsEveryNonExpiringStatus() {
            long id = 50L;
            for (Status status : Status.values()) {
                if (status == Status.EXPIRING) {
                    continue;
                }
                Request request = mockRequest(id, status);
                assertThatThrownBy(() -> service.forceCompleteRevoke(request.getRequestId()))
                        .as("status=%s", status)
                        .isInstanceOf(BusinessException.class)
                        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_REQUEST_STATUS);
                verify(request, never()).deleteAfterCleanup();
                id++;
            }
            verify(jobClient, never()).getResult(any(), any());
        }
    }
}
