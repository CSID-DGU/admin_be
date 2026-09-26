package DGU_AI_LAB.admin_be.domain.requests.service;

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
    @Mock private OperationJobService operationJobService;
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
                requestRepository, operationJobService, podExternalPortRepository, eventPublisher, transactionManager);
        when(mockRg.getServerName()).thenReturn("FARM-01");
        when(mockUser.getName()).thenReturn("테스트유저");
        when(mockUser.getEmail()).thenReturn("test@dgu.ac.kr");
        when(operationJobService.registerRevoke(any(), any())).thenReturn(900L);
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
        when(requestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));
        when(podExternalPortRepository.findByRequestRequestId(requestId)).thenReturn(List.of());
        return request;
    }

    private Request mockRequest(Long requestId, Status status) {
        return mockRequest(requestId, status, LocalDateTime.of(2026, 1, 1, 0, 0));
    }

    private static JobResultResponseDTO revokeJob(String phase, Long jobId) {
        return new JobResultResponseDTO("1", OperationJobService.KIND_REVOKE, jobId, phase, null, null, null);
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
            verify(operationJobService).registerRevoke(body.capture(), eq(ErrorCode.POD_DELETION_FAILED));
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
            verify(operationJobService, never()).registerRevoke(any(), any());
        }

        @Test
        @DisplayName("관리자 경로는 FULFILLED가 아니면 409로 알린다 — 누른 버튼은 결과를 돌려줘야 한다")
        void adminRejectsNonFulfilled() {
            mockRequest(3L, Status.EXPIRING);

            assertThatThrownBy(() -> service.deleteContainerByAdmin(3L))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_REQUEST_STATUS);
            verify(operationJobService, never()).registerRevoke(any(), any());
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
            when(operationJobService.registerRevoke(any(), any()))
                    .thenThrow(new BusinessException("작업 등록 실패", ErrorCode.POD_DELETION_FAILED));
            when(operationJobService.getResult(OperationJobService.KIND_REVOKE, 4L))
                    .thenReturn(revokeJob(OperationJobService.PHASE_NONE, null));

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
            when(operationJobService.registerRevoke(any(), any()))
                    .thenThrow(new BusinessException("이미 처리 중", ErrorCode.INVALID_REQUEST_STATUS));
            when(operationJobService.getResult(OperationJobService.KIND_REVOKE, 5L))
                    .thenReturn(revokeJob(OperationJobService.PHASE_START, 812L));

            service.deleteContainerByAdmin(5L);

            assertThat(request.getStatus()).isEqualTo(Status.EXPIRING);
            verify(request).recordJob(812L);
        }

        @Test
        @DisplayName("config-server에 연결조차 못 했으면 조회 없이 바로 FULFILLED로 되돌린다 — 작업이 없는 것이 확실하다")
        void unreachableServerRevertsImmediately() {
            Request request = mockRequest(7L, Status.FULFILLED);
            when(operationJobService.registerRevoke(any(), any())).thenThrow(new BusinessException(
                    "작업 등록 중 오류", ErrorCode.POD_DELETION_FAILED,
                    new RuntimeException(new java.net.ConnectException("Connection refused"))));

            assertThatThrownBy(() -> service.deleteContainerByAdmin(7L))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POD_DELETION_FAILED);
            assertThat(request.getStatus()).isEqualTo(Status.FULFILLED);
            verify(operationJobService, never()).getResult(any(), anyLong());
        }

        @Test
        @DisplayName("등록 실패 후 작업 상태도 모르면 EXPIRING으로 두고 예외를 올린다 — 재조정이 작업 상태를 보고 판단한다")
        void registrationAndLookupFailureKeepsExpiring() {
            Request request = mockRequest(6L, Status.FULFILLED);
            when(operationJobService.registerRevoke(any(), any())).thenThrow(new RuntimeException("timeout"));
            when(operationJobService.getResult(any(), anyLong())).thenThrow(new RuntimeException("timeout"));

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
}
