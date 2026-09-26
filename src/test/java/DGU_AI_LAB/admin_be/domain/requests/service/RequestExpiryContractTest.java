package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.domain.pod.repository.PodExternalPortRepository;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.JobResultResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import DGU_AI_LAB.admin_be.global.event.RequestExpiredEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 만료 회수 경로가 밖에서 관찰되는 호출과 상태 전이를 어떤 순서로 하는지 고정한다.
 * VASC 실험의 잔여 접근률은 회수가 실제로 시작된 신청을 분모로 쓰고, 그 시작 시점은 FULFILLED 에서
 * EXPIRING 으로 바뀌는 순간이다. 상태 전이와 회수 작업 등록의 앞뒤가 뒤집히면 분모의 뜻이 달라지므로
 * 순서 자체를 계약으로 박아 둔다.
 *
 * <p>결과 반영에서는 이벤트 발행이 반드시 상태 전이 뒤에 와야 한다. RequestEventListener 가 AFTER_COMMIT 으로
 * 붙어 있어서, 발행 위치가 앞으로 가면 통보는 나가는데 상태는 아직 바뀌지 않은 창이 열린다.
 *
 * <p>동작(인자·반환값) 검증은 {@code RequestExpiryServiceTest} 가 맡는다. 여기서는 순서만 본다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("만료 회수 호출 순서 계약")
class RequestExpiryContractTest {

    @Mock private RequestRepository requestRepository;
    @Mock private OperationJobService operationJobService;
    @Mock private PodExternalPortRepository podExternalPortRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private PlatformTransactionManager transactionManager;
    @Mock private TransactionStatus transactionStatus;

    @Mock private ResourceGroup mockRg;
    @Mock private User mockUser;

    private static final String POD_NAME = "pod-testuser-xxxx";

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

    private Request given(Long requestId, Status status) {
        Request request = mock(Request.class);
        AtomicReference<Status> current = new AtomicReference<>(status);
        when(request.getStatus()).thenAnswer(inv -> current.get());
        doAnswer(inv -> { current.set(Status.EXPIRING); return null; }).when(request).beginExpiry();
        doAnswer(inv -> { current.set(Status.FULFILLED); return null; }).when(request).endExpiry();
        doAnswer(inv -> { current.set(Status.DELETED); return null; }).when(request).deleteAfterCleanup();
        when(request.getUbuntuUsername()).thenReturn("testuser");
        when(request.getUser()).thenReturn(mockUser);
        when(request.getResourceGroup()).thenReturn(mockRg);
        when(request.getPodName()).thenReturn(POD_NAME);
        when(request.getExpiresAt()).thenReturn(LocalDateTime.of(2026, 1, 1, 0, 0));
        when(requestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));
        when(podExternalPortRepository.findByRequestRequestId(requestId)).thenReturn(List.of());
        return request;
    }

    @Test
    @DisplayName("회수 시작은 잠금 조회 → EXPIRING 선점 → 회수 작업 등록 → 작업 번호 기록 순서로 진행한다")
    void start_followsLockClaimRegisterRecordOrder() {
        Request request = given(100L, Status.FULFILLED);

        service.deleteExpiredRequest(100L);

        InOrder order = inOrder(requestRepository, request, operationJobService);
        order.verify(requestRepository).findByIdForUpdate(100L);
        order.verify(request).beginExpiry();
        order.verify(operationJobService).registerRevoke(any(), eq(ErrorCode.POD_DELETION_FAILED));
        order.verify(request).recordJob(900L);
    }

    /**
     * 되돌리기가 반드시 일어나야 하는 이유: 되돌리지 않으면 신청이 EXPIRING 에 갇혀서 다음 만료
     * 스케줄의 FULFILLED 조회에 잡히지 않고, 그러면 재시도 자체가 사라진다.
     */
    @Test
    @DisplayName("등록이 실패하면 선점 → 등록 시도 → FULFILLED 되돌림 순서로 끝나고 예외를 전파한다")
    void registrationFails_revertsAfterAttempt() {
        Request request = given(101L, Status.FULFILLED);
        when(operationJobService.registerRevoke(any(), any()))
                .thenThrow(new BusinessException(ErrorCode.POD_DELETION_FAILED));
        when(operationJobService.getResult(OperationJobService.KIND_REVOKE, 101L)).thenReturn(
                new JobResultResponseDTO("101", OperationJobService.KIND_REVOKE, null, OperationJobService.PHASE_NONE, null, null, null));

        assertThatThrownBy(() -> service.deleteExpiredRequest(101L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.POD_DELETION_FAILED);

        InOrder order = inOrder(request, operationJobService);
        order.verify(request).beginExpiry();
        order.verify(operationJobService).registerRevoke(any(), any());
        order.verify(request).endExpiry();
        assertThat(request.getStatus()).isEqualTo(Status.FULFILLED);
    }

    @Test
    @DisplayName("결과 반영은 잠금 조회 → DELETED 전이 → 포트 회수 → 통보 발행 순서로 진행한다")
    void complete_followsLockFinalizeReleasePublishOrder() {
        Request request = given(102L, Status.EXPIRING);

        service.completeContainerRevoke(102L);

        InOrder order = inOrder(requestRepository, request, podExternalPortRepository, eventPublisher);
        order.verify(requestRepository).findByIdForUpdate(102L);
        order.verify(request).deleteAfterCleanup();
        order.verify(podExternalPortRepository).deleteByRequestRequestId(102L);
        order.verify(eventPublisher).publishEvent(any(Object.class));

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(RequestExpiredEvent.class);
    }
}
