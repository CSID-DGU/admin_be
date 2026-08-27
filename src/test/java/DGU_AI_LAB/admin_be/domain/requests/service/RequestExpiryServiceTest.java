package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.domain.pod.repository.PodExternalPortRepository;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import DGU_AI_LAB.admin_be.error.exception.EntityNotFoundException;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 만료 리소스 정리(deleteExpiredRequest)가 외부 삭제 실패 시 DB를 DELETED로
 * 조용히 전환하지 않고, 반드시 예외를 던져 상위 스케줄러의 관리자 알림 경로가
 * 실행되도록 하는지 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RequestExpiryService")
class RequestExpiryServiceTest {

    @Mock private RequestRepository requestRepository;
    @Mock private UbuntuAccountService ubuntuAccountService;
    @Mock private PodService podService;
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
                requestRepository, ubuntuAccountService, podService,
                podExternalPortRepository, eventPublisher, transactionManager
        );
        when(mockRg.getServerName()).thenReturn("FARM-01");
        when(mockUser.getName()).thenReturn("테스트유저");
        when(mockUser.getEmail()).thenReturn("test@dgu.ac.kr");
    }

    private Request buildMockedRequest(Status status) {
        Request request = mock(Request.class);
        when(request.getStatus()).thenReturn(status);
        when(request.getUbuntuUsername()).thenReturn("testuser");
        when(request.getUser()).thenReturn(mockUser);
        when(request.getResourceGroup()).thenReturn(mockRg);
        when(request.getPodName()).thenReturn("pod-testuser-xxxx");
        when(request.getExpiresAt()).thenReturn(LocalDateTime.of(2026, 1, 1, 0, 0));
        return request;
    }

    @Nested
    @DisplayName("정상/제외 흐름")
    class HappyPath {

        @Test
        @DisplayName("Pod 삭제와 계정 삭제가 모두 성공하면 DELETED로 전환되고 이벤트가 발행된다")
        void bothDeletesSucceed_marksDeletedAndPublishesEvent() {
            Long requestId = 1L;
            Request request = buildMockedRequest(Status.FULFILLED);
            when(requestRepository.findById(requestId)).thenReturn(Optional.of(request));
            when(podExternalPortRepository.findByRequestRequestId(requestId)).thenReturn(List.of());

            service.deleteExpiredRequest(requestId);

            verify(podService).deletePod("pod-testuser-xxxx");
            verify(ubuntuAccountService).deleteUbuntuAccount("testuser");
            verify(request).deleteAfterCleanup();

            ArgumentCaptor<RequestExpiredEvent> captor = ArgumentCaptor.forClass(RequestExpiredEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());
            assertThat(captor.getValue().ubuntuUsername()).isEqualTo("testuser");
            assertThat(captor.getValue().serverName()).isEqualTo("FARM-01");
        }

        @Test
        @DisplayName("PENDING 상태 요청은 정리 대상이 아니므로 아무 것도 하지 않는다")
        void pendingStatus_doesNothing() {
            Long requestId = 2L;
            Request request = buildMockedRequest(Status.PENDING);
            when(requestRepository.findById(requestId)).thenReturn(Optional.of(request));

            service.deleteExpiredRequest(requestId);

            verify(podService, never()).deletePod(any());
            verify(ubuntuAccountService, never()).deleteUbuntuAccount(any());
            verify(request, never()).deleteAfterCleanup();
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("이미 DELETED 상태인 요청은 정리 대상이 아니므로 아무 것도 하지 않는다")
        void alreadyDeletedStatus_doesNothing() {
            Long requestId = 3L;
            Request request = buildMockedRequest(Status.DELETED);
            when(requestRepository.findById(requestId)).thenReturn(Optional.of(request));

            service.deleteExpiredRequest(requestId);

            verify(podService, never()).deletePod(any());
            verify(ubuntuAccountService, never()).deleteUbuntuAccount(any());
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("PROCESSING 상태인 요청도 정리 대상이 아니므로 아무 것도 하지 않는다")
        void processingStatus_doesNothing() {
            Long requestId = 4L;
            Request request = buildMockedRequest(Status.PROCESSING);
            when(requestRepository.findById(requestId)).thenReturn(Optional.of(request));

            service.deleteExpiredRequest(requestId);

            verify(podService, never()).deletePod(any());
            verify(ubuntuAccountService, never()).deleteUbuntuAccount(any());
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("존재하지 않는 requestId면 EntityNotFoundException을 던진다")
        void requestNotFound_throwsEntityNotFoundException() {
            Long requestId = 999L;
            when(requestRepository.findById(requestId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.deleteExpiredRequest(requestId))
                    .isInstanceOf(EntityNotFoundException.class);

            verify(podService, never()).deletePod(any());
            verify(eventPublisher, never()).publishEvent(any());
        }
    }

    @Nested
    @DisplayName("부분 실패 시 고아 리소스 방지")
    class PartialFailure {

        @Test
        @DisplayName("Pod 삭제 실패 시 예외를 던지고, 계정 삭제는 시도하지 않으며, DELETED로 전환하지 않는다")
        void podDeletionFails_throwsAndDoesNotMarkDeleted() {
            Long requestId = 10L;
            Request request = buildMockedRequest(Status.FULFILLED);
            when(requestRepository.findById(requestId)).thenReturn(Optional.of(request));
            when(podExternalPortRepository.findByRequestRequestId(requestId)).thenReturn(List.of());
            doThrow(new BusinessException(ErrorCode.POD_DELETION_FAILED))
                    .when(podService).deletePod("pod-testuser-xxxx");

            assertThatThrownBy(() -> service.deleteExpiredRequest(requestId))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.POD_DELETION_FAILED);

            verify(ubuntuAccountService, never()).deleteUbuntuAccount(any());
            verify(request, never()).deleteAfterCleanup();
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("계정 삭제 실패 시 예외를 던지고 DELETED로 전환하지 않는다 (Pod 삭제는 이미 수행됨)")
        void accountDeletionFails_throwsAndDoesNotMarkDeleted() {
            Long requestId = 11L;
            Request request = buildMockedRequest(Status.FULFILLED);
            when(requestRepository.findById(requestId)).thenReturn(Optional.of(request));
            when(podExternalPortRepository.findByRequestRequestId(requestId)).thenReturn(List.of());
            doThrow(new RuntimeException("WAS 연결 실패"))
                    .when(ubuntuAccountService).deleteUbuntuAccount("testuser");

            assertThatThrownBy(() -> service.deleteExpiredRequest(requestId))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.UBUNTU_USER_DELETION_FAILED);

            verify(podService).deletePod("pod-testuser-xxxx");
            verify(request, never()).deleteAfterCleanup();
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("Pod 삭제와 계정 삭제가 모두 실패 대상이어도 Pod 삭제 예외가 먼저 전파되고 계정 삭제는 시도되지 않는다")
        void bothDeletionsWouldFail_podFailurePropagatesFirstWithoutTryingAccount() {
            Long requestId = 12L;
            Request request = buildMockedRequest(Status.FULFILLED);
            when(requestRepository.findById(requestId)).thenReturn(Optional.of(request));
            when(podExternalPortRepository.findByRequestRequestId(requestId)).thenReturn(List.of());
            doThrow(new BusinessException(ErrorCode.POD_DELETION_FAILED))
                    .when(podService).deletePod("pod-testuser-xxxx");

            assertThatThrownBy(() -> service.deleteExpiredRequest(requestId))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.POD_DELETION_FAILED);

            verify(ubuntuAccountService, never()).deleteUbuntuAccount(any());
        }
    }
}
