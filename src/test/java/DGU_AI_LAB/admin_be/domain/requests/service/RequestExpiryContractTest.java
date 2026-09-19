package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.domain.pod.repository.PodExternalPortRepository;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import DGU_AI_LAB.admin_be.global.event.RequestContainerDeletedEvent;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 만료 회수 경로(cleanupContainer)가 밖에서 관찰되는 호출과 상태 전이를 어떤 순서로 하는지 고정한다.
 * VASC 실험의 잔여 접근률은 회수가 실제로 시작된 신청을 분모로 쓰고, 그 시작 시점은 FULFILLED 에서
 * EXPIRING 으로 바뀌는 순간이다. 상태 전이와 외부 삭제 호출의 앞뒤가 뒤집히면 분모의 뜻이 달라지므로
 * 순서 자체를 계약으로 박아 둔다.
 *
 * <p>특히 이벤트 발행은 반드시 상태 전이 뒤에 와야 한다. RequestEventListener 가 AFTER_COMMIT 으로
 * 붙어 있어서, 발행 위치가 앞으로 가면 통보는 나가는데 상태는 아직 바뀌지 않은 창이 열린다.
 *
 * <p>동작(인자·반환값) 검증은 {@code RequestExpiryServiceTest} 가 맡는다. "만료는 우분투 계정을
 * 지우지 않는다" 는 주장은 그 파일의 {@code AccountSurvivesExpiry} 가 이미 고정하고 있으므로
 * 여기에 다시 쓰지 않는다. 여기서는 순서만 본다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("만료 회수 호출 순서 계약")
class RequestExpiryContractTest {

    @Mock private RequestRepository requestRepository;
    @Mock private PodService podService;
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
                requestRepository, podService,
                podExternalPortRepository, eventPublisher, transactionManager
        );
        when(mockRg.getServerName()).thenReturn("FARM-01");
        when(mockUser.getName()).thenReturn("테스트유저");
        when(mockUser.getEmail()).thenReturn("test@dgu.ac.kr");
    }

    /** 상태가 고정된 mock 으로는 FULFILLED -> EXPIRING 선점 후 EXPIRING 을 재확인하는 흐름이 재현되지 않는다. */
    private Request buildMockedRequest(Status status) {
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
        return request;
    }

    private Request givenFulfilled(Long requestId) {
        Request request = buildMockedRequest(Status.FULFILLED);
        when(requestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));
        when(podExternalPortRepository.findByRequestRequestId(requestId)).thenReturn(List.of());
        return request;
    }

    @Test
    @DisplayName("만료 정리 1회는 잠금 조회 → EXPIRING 선점 → Pod 삭제 → DELETED 전이 → 만료 통보 발행 순서로 진행한다")
    void expiryCleanup_followsLockClaimDeleteFinalizePublishOrder() {
        Long requestId = 100L;
        Request request = givenFulfilled(requestId);

        service.deleteExpiredRequest(requestId);

        InOrder order = inOrder(requestRepository, request, podService, eventPublisher);
        order.verify(requestRepository).findByIdForUpdate(requestId);
        order.verify(request).beginExpiry();
        order.verify(podService).deletePod(POD_NAME, requestId);
        order.verify(request).deleteAfterCleanup();
        order.verify(eventPublisher).publishEvent(any(Object.class));

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(RequestExpiredEvent.class);
    }

    /**
     * 되돌리기가 반드시 일어나야 하는 이유: 되돌리지 않으면 신청이 EXPIRING 에 갇혀서 다음 만료
     * 스케줄의 FULFILLED 조회에 잡히지 않고, 그러면 재시도 자체가 사라진다.
     */
    @Test
    @DisplayName("Pod 삭제가 실패하면 DELETED 전이도 통보도 하지 않고 FULFILLED 로 되돌린 뒤 예외를 전파한다")
    void podDeleteFails_revertsToFulfilledAndPropagates_soNextScheduleRetries() {
        Long requestId = 101L;
        Request request = givenFulfilled(requestId);
        doThrow(new BusinessException(ErrorCode.POD_DELETION_FAILED))
                .when(podService).deletePod(eq(POD_NAME), any());

        assertThatThrownBy(() -> service.deleteExpiredRequest(requestId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.POD_DELETION_FAILED);

        verify(request, never()).deleteAfterCleanup();
        verify(eventPublisher, never()).publishEvent(any());

        InOrder order = inOrder(podService, request);
        order.verify(podService).deletePod(eq(POD_NAME), any());
        order.verify(request).endExpiry();
        assertThat(request.getStatus()).isEqualTo(Status.FULFILLED);
    }

    @Test
    @DisplayName("외부 삭제가 도는 동안 다른 경로가 상태를 바꿨으면 DELETED 전이를 거부하고 예외를 낸다")
    void statusChangedDuringInfraDelete_refusesToOverwriteThatDecision() {
        Long requestId = 102L;
        Request request = givenFulfilled(requestId);
        // 외부 삭제가 도는 사이 다른 트랜잭션이 상태를 DENIED 로 바꾼 상황을 재현한다.
        doAnswer(inv -> {
            when(request.getStatus()).thenReturn(Status.DENIED);
            return null;
        }).when(podService).deletePod(eq(POD_NAME), any());

        assertThatThrownBy(() -> service.deleteExpiredRequest(requestId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST_STATUS);

        verify(request, never()).deleteAfterCleanup();
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("관리자 회수도 같은 순서로 진행하고 통보 이벤트만 회수 안내로 달라진다")
    void adminDelete_sameOrder_onlyTheNotificationEventDiffers() {
        Long requestId = 103L;
        Request request = givenFulfilled(requestId);

        service.deleteContainerByAdmin(requestId);

        InOrder order = inOrder(requestRepository, request, podService, eventPublisher);
        order.verify(requestRepository).findByIdForUpdate(requestId);
        order.verify(request).beginExpiry();
        order.verify(podService).deletePod(POD_NAME, requestId);
        order.verify(request).deleteAfterCleanup();
        order.verify(eventPublisher).publishEvent(any(Object.class));

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(RequestContainerDeletedEvent.class);
    }

    @Test
    @DisplayName("대상이 FULFILLED 가 아니면 만료 경로는 조용히 넘어가고 관리자 경로는 예외를 낸다")
    void notFulfilled_expiryPathSkipsQuietly_adminPathThrows() {
        Long expiryTarget = 104L;
        Request pendingForExpiry = buildMockedRequest(Status.PENDING);
        when(requestRepository.findByIdForUpdate(expiryTarget)).thenReturn(Optional.of(pendingForExpiry));

        service.deleteExpiredRequest(expiryTarget);

        verify(pendingForExpiry, never()).beginExpiry();
        verify(podService, never()).deletePod(any(), any());
        verify(eventPublisher, never()).publishEvent(any());

        Long adminTarget = 105L;
        Request pendingForAdmin = buildMockedRequest(Status.PENDING);
        when(requestRepository.findByIdForUpdate(adminTarget)).thenReturn(Optional.of(pendingForAdmin));

        assertThatThrownBy(() -> service.deleteContainerByAdmin(adminTarget))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST_STATUS);

        verify(pendingForAdmin, never()).beginExpiry();
        verify(podService, never()).deletePod(any(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }
}
