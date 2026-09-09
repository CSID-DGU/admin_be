package DGU_AI_LAB.admin_be.domain.users.service;

import DGU_AI_LAB.admin_be.domain.alarm.service.AlarmService;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.requests.service.PodService;
import DGU_AI_LAB.admin_be.domain.requests.service.UbuntuAccountService;
import DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup;
import DGU_AI_LAB.admin_be.domain.users.dto.request.UserUpdateRequestDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.response.UserResponseDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.response.UserSummaryDTO;
import DGU_AI_LAB.admin_be.domain.users.entity.Role;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import DGU_AI_LAB.admin_be.error.exception.ConflictException;
import DGU_AI_LAB.admin_be.error.exception.EntityNotFoundException;
import DGU_AI_LAB.admin_be.global.util.MessageUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @InjectMocks
    private AdminUserService adminUserService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RequestRepository requestRepository;

    @Mock
    private UbuntuAccountService ubuntuAccountService;

    @Mock
    private PodService podService;

    @Mock
    private AlarmService alarmService;

    @Mock
    private MessageUtils messageUtils;

    @Mock
    private TokenService tokenService;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private TransactionStatus transactionStatus;

    private User mockUser;
    private long nextRequestId = 1000L;

    @BeforeEach
    void setUp() {
        mockUser = User.builder()
                .email("test@dgu.ac.kr")
                .password("encodedPassword")
                .name("홍길동")
                .studentId("2021001234")
                .phone("010-1234-5678")
                .department("컴퓨터공학과")
                .ubuntuUsername("testuser")
                .build();
        // 승인을 한 번이라도 받은 사용자는 웹 계정에 리눅스 계정(UID/GID)이 물려 있다.
        // 이 계정 회수는 요청 만료가 아니라 사용자 삭제/비활성화에서만 일어난다.
        mockUser.assignUbuntuAccount(20001L, 20001L);
        lenient().when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        // 계정 회수 단계는 User 행을 잠그고 수행한다.
        lenient().when(userRepository.findByIdForUpdate(any())).thenReturn(Optional.of(mockUser));
    }

    @Nested
    @DisplayName("getAllUsers")
    class GetAllUsers {

        @Test
        @DisplayName("유저 목록이 있으면 UserSummaryDTO 리스트를 반환한다")
        void getAllUsers_returnsUserList() {
            User user2 = User.builder()
                    .email("user2@dgu.ac.kr")
                    .password("pw")
                    .name("이순신")
                    .studentId("2021005678")
                    .phone("010-5678-1234")
                    .department("전자공학과")
                    .build();

            when(userRepository.findAll()).thenReturn(List.of(mockUser, user2));

            List<UserSummaryDTO> result = adminUserService.getAllUsers();

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("유저가 없으면 빈 리스트를 반환한다")
        void getAllUsers_returnsEmptyList_whenNoUsers() {
            when(userRepository.findAll()).thenReturn(List.of());

            List<UserSummaryDTO> result = adminUserService.getAllUsers();

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("updateUser")
    class UpdateUser {

        @Test
        @DisplayName("유저가 존재하면 정보를 수정하고 UserResponseDTO를 반환한다")
        void updateUser_success() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));

            UserUpdateRequestDTO request = new UserUpdateRequestDTO("newPw", false);
            UserResponseDTO result = adminUserService.updateUser(1L, request);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("계정을 비활성화하면 리프레시 토큰도 함께 폐기한다")
        void updateUser_revokesRefreshToken_whenDeactivated() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));

            adminUserService.updateUser(1L, new UserUpdateRequestDTO("newPw", false));

            verify(tokenService).logout(1L);
        }

        @Test
        @DisplayName("계정이 활성 상태로 유지되면 리프레시 토큰을 건드리지 않는다")
        void updateUser_keepsRefreshToken_whenStillActive() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));

            adminUserService.updateUser(1L, new UserUpdateRequestDTO("newPw", true));

            verify(tokenService, never()).logout(anyLong());
        }

        @Test
        @DisplayName("유저가 없으면 EntityNotFoundException을 던진다")
        void updateUser_throwsException_whenUserNotFound() {
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            UserUpdateRequestDTO request = new UserUpdateRequestDTO("newPw", false);

            assertThatThrownBy(() -> adminUserService.updateUser(99L, request))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    /**
     * FULFILLED 상태이며 우분투 계정/Pod 정리 대상인 Request 목을 만든다.
     * deleteUser/deactivateUser 테스트가 공유한다.
     */
    /**
     * 상태 전이를 실제 엔티티처럼 흉내내는 FULFILLED 요청 mock. cleanupUserRequests가
     * FULFILLED -> EXPIRING 선점 후 deleteAfterCleanup()을 부르는 구조라, 상태가 고정된
     * mock으로는 정상 흐름이 재현되지 않는다.
     */
    private Request mockFulfilledRequest(String username, long requestId) {
        return mockFulfilledRequest(username, requestId, "farm1");
    }

    private Request mockFulfilledRequest(String username, long requestId, String nodeName) {
        Request request = mock(Request.class);
        AtomicReference<Status> current = new AtomicReference<>(Status.FULFILLED);
        when(request.getStatus()).thenAnswer(inv -> current.get());
        lenient().doAnswer(inv -> { current.set(Status.EXPIRING); return null; }).when(request).beginExpiry();
        lenient().doAnswer(inv -> { current.set(Status.FULFILLED); return null; }).when(request).endExpiry();
        when(request.getRequestId()).thenReturn(requestId);
        // 선점(beginExpiry)에서 걸러진 요청은 인프라 삭제까지 가지 않아 이 스텁들이 안 쓰일 수 있다.
        lenient().when(request.getUbuntuUsername()).thenReturn(username);
        lenient().when(request.getPodName()).thenReturn("pod-" + username);
        // 계정 삭제 시 config-server에 넘길 farm 노드 — 없으면 모든 노드를 훑게 되어 삭제를 보류한다.
        lenient().when(request.getNodeName()).thenReturn(nodeName);
        // 정리 완료 트랜잭션에서 메일 발송용 lazy 연관을 초기화한다.
        lenient().when(request.getUser()).thenReturn(mockUser);
        lenient().when(request.getResourceGroup()).thenReturn(mock(ResourceGroup.class));
        // cleanupUserRequests가 REQUIRES_NEW 트랜잭션 안에서 행 잠금으로 다시 조회한 뒤
        // beginExpiry()/deleteAfterCleanup()을 호출하므로, 같은 mock을 반환하도록 스텁해야
        // 이후 verify가 이 인스턴스에 대해 성립한다.
        lenient().when(requestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));
        return request;
    }

    private Request mockRequestWithStatus(Status status) {
        Request request = mock(Request.class);
        when(request.getStatus()).thenReturn(status);
        long requestId = nextRequestId++;
        lenient().when(request.getRequestId()).thenReturn(requestId);
        lenient().when(requestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));
        return request;
    }

    @Nested
    @DisplayName("deleteUser")
    class DeleteUser {

        @Test
        @DisplayName("연결된 Request가 없어도 유저를 삭제하면 우분투 계정을 회수하고 isActive를 false로 변경한다")
        void deleteUser_withNoRequests_softDeletesAndReleasesAccount() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
            when(requestRepository.findAllByUser(mockUser)).thenReturn(List.of());
            // 컨테이너는 이미 만료됐지만 지난 신청 이력에 노드가 남아 있어 삭제 범위를 좁힐 수 있다.
            when(requestRepository.findNodeNamesByUserIdOrderByRequestIdDesc(any())).thenReturn(List.of("farm1"));
            when(messageUtils.get(anyString(), any(Object[].class))).thenReturn("mock");

            adminUserService.deleteUser(1L);

            assertThat(mockUser.getIsActive()).isFalse();
            assertThat(mockUser.getDeletedAt()).isNotNull();
            verifyNoInteractions(podService);
            // 컨테이너는 이미 만료로 정리됐어도 리눅스 계정은 웹 계정에 남아 있다 —
            // 사용자 삭제가 그 계정을 실제로 회수하는 유일한 지점이다.
            verify(ubuntuAccountService).deleteUbuntuAccount("testuser", "farm1");
            assertThat(mockUser.hasUbuntuAccount()).isFalse();
            assertThat(mockUser.getUbuntuUsername()).isEqualTo("testuser");
            verify(alarmService).sendAllAlerts(eq("홍길동"), eq("test@dgu.ac.kr"), anyString(), anyString());
        }

        @Test
        @DisplayName("우분투 계정이 배정된 적 없는 유저는 계정 삭제 API를 호출하지 않는다")
        void deleteUser_withoutAssignedAccount_skipsAccountDeletion() {
            mockUser.releaseUbuntuAccount();
            when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
            when(requestRepository.findAllByUser(mockUser)).thenReturn(List.of());
            when(messageUtils.get(anyString(), any(Object[].class))).thenReturn("mock");

            adminUserService.deleteUser(1L);

            verifyNoInteractions(ubuntuAccountService, podService);
        }

        @Test
        @DisplayName("계정이 배포된 farm 노드를 알 수 없으면 계정 삭제를 보류하고 관리자에게 알린다 — 모든 노드를 훑으면 동명의 레거시 계정까지 지운다")
        void deleteUser_withUnknownAccountNode_skipsDeletionAndAlerts() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
            when(requestRepository.findAllByUser(mockUser)).thenReturn(List.of());
            when(requestRepository.findNodeNamesByUserIdOrderByRequestIdDesc(any())).thenReturn(List.of());
            when(messageUtils.get(anyString(), any(Object[].class))).thenReturn("mock");

            adminUserService.deleteUser(1L);

            verifyNoInteractions(ubuntuAccountService, podService);
            verify(alarmService).sendSlackAlert(contains("testuser"), isNull());
            // 상태를 그대로 남겨야 관리자가 수동 정리 후 재시도할 수 있다.
            assertThat(mockUser.hasUbuntuAccount()).isTrue();
            // 사용자 탈퇴 자체는 계정 정리 보류와 무관하게 완료된다.
            assertThat(mockUser.getIsActive()).isFalse();
        }

        @Test
        @DisplayName("유저를 삭제하면 남아있는 리프레시 토큰도 함께 폐기한다")
        void deleteUser_revokesRefreshToken() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
            when(requestRepository.findAllByUser(mockUser)).thenReturn(List.of());
            // 컨테이너는 이미 만료됐지만 지난 신청 이력에 노드가 남아 있어 삭제 범위를 좁힐 수 있다.
            when(requestRepository.findNodeNamesByUserIdOrderByRequestIdDesc(any())).thenReturn(List.of("farm1"));
            when(messageUtils.get(anyString(), any(Object[].class))).thenReturn("mock");

            adminUserService.deleteUser(1L);

            verify(tokenService).logout(1L);
        }

        @Test
        @DisplayName("존재하지 않는 유저를 삭제하려 하면 EntityNotFoundException을 던진다")
        void deleteUser_throwsException_whenUserNotFound() {
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> adminUserService.deleteUser(99L))
                    .isInstanceOf(EntityNotFoundException.class);
        }

        @Test
        @DisplayName("FULFILLED 상태 Request가 있으면 Pod를 지우고 마지막에 우분투 계정을 회수한다")
        void deleteUser_withFulfilledRequest_deletesPodThenReleasesAccount() {
            Request fulfilledRequest = mockFulfilledRequest("testuser", 1L);

            when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
            when(requestRepository.findAllByUser(mockUser)).thenReturn(List.of(fulfilledRequest));
            when(messageUtils.get(anyString(), any(Object[].class))).thenReturn("mock");

            adminUserService.deleteUser(1L);

            // Pod 삭제가 먼저, 계정 삭제는 모든 요청을 정리한 뒤 한 번만.
            InOrder order = inOrder(podService, ubuntuAccountService);
            order.verify(podService).deletePod("pod-testuser");
            order.verify(ubuntuAccountService).deleteUbuntuAccount("testuser", "farm1");
            verify(ubuntuAccountService, times(1)).deleteUbuntuAccount(anyString(), anyString());
            verify(fulfilledRequest).deleteAfterCleanup();
            verify(alarmService).sendContainerDeletedEmail(fulfilledRequest);
            assertThat(mockUser.getIsActive()).isFalse();
        }

        @Test
        @DisplayName("PENDING 상태 Request가 있으면 delete()를 호출한다 (외부 API 미호출)")
        void deleteUser_withPendingRequest_callsDelete() {
            Request pendingRequest = mockRequestWithStatus(Status.PENDING);

            when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
            when(requestRepository.findAllByUser(mockUser)).thenReturn(List.of(pendingRequest));
            when(messageUtils.get(anyString(), any(Object[].class))).thenReturn("mock");

            adminUserService.deleteUser(1L);

            verify(pendingRequest).delete();
            verifyNoInteractions(podService);
        }

        @Test
        @DisplayName("DELETED 상태 Request는 아무 처리도 하지 않는다")
        void deleteUser_withDeletedRequest_skips() {
            Request deletedRequest = mockRequestWithStatus(Status.DELETED);

            when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
            when(requestRepository.findAllByUser(mockUser)).thenReturn(List.of(deletedRequest));
            when(messageUtils.get(anyString(), any(Object[].class))).thenReturn("mock");

            adminUserService.deleteUser(1L);

            verify(deletedRequest, never()).delete();
            verify(deletedRequest, never()).deleteAfterCleanup();
            verifyNoInteractions(podService);
        }

        @Test
        @DisplayName("여러 상태의 Request가 혼합되면 각각 적절히 처리한다")
        void deleteUser_withMixedRequests_handlesEachCorrectly() {
            Request fulfilled = mockFulfilledRequest("fuser", 10L);

            Request pending = mockRequestWithStatus(Status.PENDING);
            Request deleted = mockRequestWithStatus(Status.DELETED);

            when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
            when(requestRepository.findAllByUser(mockUser)).thenReturn(List.of(fulfilled, pending, deleted));
            when(messageUtils.get(anyString(), any(Object[].class))).thenReturn("mock");

            adminUserService.deleteUser(1L);

            verify(podService).deletePod("pod-fuser");
            // 계정 삭제 대상은 요청이 아니라 웹 계정의 유저네임이고, 요청이 몇 개든 한 번만 부른다.
            verify(ubuntuAccountService, times(1)).deleteUbuntuAccount("testuser", "farm1");
            verify(fulfilled).deleteAfterCleanup();
            verify(alarmService).sendContainerDeletedEmail(fulfilled);
            verify(pending).delete();
            verify(deleted, never()).delete();
            verify(deleted, never()).deleteAfterCleanup();
        }

        @Test
        @DisplayName("FULFILLED 요청이 서로 다른 노드에 떠 있으면, 계정 삭제를 마지막 노드 한 곳에서만 하지 않고 노드마다 각각 호출한다")
        void deleteUser_withRequestsOnDifferentNodes_releasesAccountOnEachNode() {
            Request onFarm1 = mockFulfilledRequest("testuser", 40L, "farm1");
            Request onFarm2 = mockFulfilledRequest("testuser", 41L, "farm2");

            when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
            when(requestRepository.findAllByUser(mockUser)).thenReturn(List.of(onFarm1, onFarm2));
            when(messageUtils.get(anyString(), any(Object[].class))).thenReturn("mock");

            adminUserService.deleteUser(1L);

            verify(ubuntuAccountService).deleteUbuntuAccount("testuser", "farm1");
            verify(ubuntuAccountService).deleteUbuntuAccount("testuser", "farm2");
            verify(ubuntuAccountService, times(2)).deleteUbuntuAccount(anyString(), anyString());
            assertThat(mockUser.hasUbuntuAccount()).isFalse();
        }

        @Test
        @DisplayName("FULFILLED 요청이 여러 개일 때 하나의 Pod/계정 삭제가 실패해도, 이미 정리된 다른 요청의 DB 반영은 롤백되지 않는다")
        void deleteUser_oneOfMultipleFulfilledFails_doesNotRollbackAlreadyCleanedOnes() {
            Request ok = mockFulfilledRequest("okuser", 30L);
            Request broken = mockFulfilledRequest("brokenuser", 31L);
            // deleteUser는 cleanupUserRequests가 예외를 던지면 user.withdraw()/알림 발송까지
            // 도달하지 않으므로 messageUtils는 이 테스트에서 쓰이지 않는다.
            // strict stubbing 하에서 exact-arg doThrow는 다른 인자로 들어오는 "ok" 쪽 호출까지
            // PotentialStubbingProblem으로 오탐하므로, anyString()에 answer로 분기한다.
            doAnswer(invocation -> {
                String podName = invocation.getArgument(0);
                if ("pod-brokenuser".equals(podName)) {
                    throw new RuntimeException("config-server 통신 오류");
                }
                return null;
            }).when(podService).deletePod(anyString());

            when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
            when(requestRepository.findAllByUser(mockUser)).thenReturn(List.of(ok, broken));

            assertThatThrownBy(() -> adminUserService.deleteUser(1L))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.USER_REQUEST_CLEANUP_PARTIALLY_FAILED);

            // ok는 REQUIRES_NEW로 독립 커밋되므로, broken이 실패해서 메서드 전체가 예외로
            // 끝나도 이미 실행된 deleteAfterCleanup()은 그대로 유지돼야 한다(고아 방지의 핵심).
            verify(ok).deleteAfterCleanup();
            // 정리되지 않은 Pod가 하나라도 남으면 계정을 회수하지 않는다 — 계정 없이 떠 있는 Pod가 된다.
            verifyNoInteractions(ubuntuAccountService);
            verify(broken, never()).deleteAfterCleanup();
            verify(alarmService).sendSlackAlert(contains("brokenuser"), isNull());
        }

        @Test
        @DisplayName("사전 검사 이후 승인이 시작된 요청은 인프라를 지우지 않고 건너뛴다 — 일괄 검사만으로는 못 막는 경합")
        void deleteUser_requestTurnsInFlightMidLoop_skipsItsInfraDeletion() {
            Request ok = mockFulfilledRequest("okuser", 40L);
            Request raced = mockFulfilledRequest("raceduser", 41L);
            // 사전 검사는 FULFILLED로 통과했지만, 이 요청 차례가 왔을 때는 이미 승인이 시작돼
            // 행 잠금 후의 beginExpiry()가 거부하는 상황을 재현한다.
            doThrow(new BusinessException(ErrorCode.INVALID_REQUEST_STATUS)).when(raced).beginExpiry();

            when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
            when(requestRepository.findAllByUser(mockUser)).thenReturn(List.of(ok, raced));

            assertThatThrownBy(() -> adminUserService.deleteUser(1L))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.USER_REQUEST_CLEANUP_PARTIALLY_FAILED);

            // 선점에 실패한 요청은 Pod/계정을 건드리지 않는다 (고아 인프라 방지의 핵심)
            verify(podService, never()).deletePod("pod-raceduser");
            verifyNoInteractions(ubuntuAccountService);
            verify(raced, never()).deleteAfterCleanup();
            // 정상 요청은 그대로 정리된다
            verify(podService).deletePod("pod-okuser");
            verify(ok).deleteAfterCleanup();
        }

        @Test
        @DisplayName("MIGRATING 상태 Request가 있으면 삭제 자체를 거부하고 유저를 건드리지 않는다")
        void deleteUser_withMigratingRequest_rejectsAndLeavesUserUntouched() {
            Request migrating = mockRequestWithStatus(Status.MIGRATING);
            Request fulfilled = mock(Request.class);

            when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
            when(requestRepository.findAllByUser(mockUser)).thenReturn(List.of(migrating, fulfilled));

            assertThatThrownBy(() -> adminUserService.deleteUser(1L))
                    .isInstanceOf(ConflictException.class)
                    .extracting(e -> ((ConflictException) e).getErrorCode())
                    .isEqualTo(ErrorCode.REQUEST_MIGRATION_IN_PROGRESS);

            assertThat(mockUser.getIsActive()).isNotEqualTo(false);
            verify(fulfilled, never()).deleteAfterCleanup();
            verifyNoInteractions(ubuntuAccountService, podService, tokenService);
        }

        @Test
        @DisplayName("PROCESSING 상태 Request가 있으면 삭제 자체를 거부하고 유저를 건드리지 않는다")
        void deleteUser_withProcessingRequest_rejectsAndLeavesUserUntouched() {
            Request processing = mockRequestWithStatus(Status.PROCESSING);
            Request fulfilled = mock(Request.class);

            when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
            when(requestRepository.findAllByUser(mockUser)).thenReturn(List.of(processing, fulfilled));

            assertThatThrownBy(() -> adminUserService.deleteUser(1L))
                    .isInstanceOf(ConflictException.class)
                    .extracting(e -> ((ConflictException) e).getErrorCode())
                    .isEqualTo(ErrorCode.REQUEST_MIGRATION_IN_PROGRESS);

            assertThat(mockUser.getIsActive()).isNotEqualTo(false);
            verify(fulfilled, never()).deleteAfterCleanup();
            verifyNoInteractions(ubuntuAccountService, podService, tokenService);
        }

        @Test
        @DisplayName("FULFILLED 요청마다 인프라 삭제 직전에 행을 잠그고 EXPIRING으로 선점한다")
        void deleteUser_locksAndClaimsEachRequestBeforeDeletingItsInfra() {
            Request req1 = mockFulfilledRequest("user1", 1L);
            Request req2 = mockFulfilledRequest("user2", 2L);
            Request req3 = mockFulfilledRequest("user3", 3L);

            when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
            when(requestRepository.findAllByUser(mockUser)).thenReturn(List.of(req1, req2, req3));
            when(messageUtils.get(anyString(), any(Object[].class))).thenReturn("mock");

            adminUserService.deleteUser(1L);

            // 요청마다 선점(beginExpiry)이 그 요청의 Pod 삭제보다 먼저 일어나야, 사전 검사 이후
            // 새로 시작된 승인/마이그레이션이 실제로 차단된다.
            for (Request req : List.of(req1, req2, req3)) {
                InOrder order = inOrder(req, podService);
                order.verify(req).beginExpiry();
                order.verify(podService).deletePod("pod-" + req.getUbuntuUsername());
                verify(requestRepository, atLeastOnce()).findByIdForUpdate(req.getRequestId());
            }
            verify(requestRepository, never()).findById(anyLong());
            verify(alarmService, times(3)).sendContainerDeletedEmail(any(Request.class));
            verify(podService, times(3)).deletePod(anyString());
        }

        @Test
        @DisplayName("컨테이너 삭제 안내 메일 발송이 실패해도 계정 삭제와 탈퇴는 계속 진행된다")
        void deleteUser_continuesCleanup_whenContainerEmailFails() {
            Request req1 = mockFulfilledRequest("user1", 1L);
            Request req2 = mockFulfilledRequest("user2", 2L);

            when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
            when(requestRepository.findAllByUser(mockUser)).thenReturn(List.of(req1, req2));
            when(messageUtils.get(anyString(), any(Object[].class))).thenReturn("mock");
            doThrow(new RuntimeException("메일 서버 오류"))
                    .when(alarmService).sendContainerDeletedEmail(req1);

            adminUserService.deleteUser(1L);

            verify(podService).deletePod("pod-user1");
            verify(podService).deletePod("pod-user2");
            // 요청이 두 개여도 리눅스 계정은 하나뿐이라 계정 삭제는 마지막에 한 번만 일어난다.
            verify(ubuntuAccountService, times(1)).deleteUbuntuAccount("testuser", "farm1");
            verify(req1).deleteAfterCleanup();
            verify(req2).deleteAfterCleanup();
            assertThat(mockUser.getIsActive()).isFalse();
        }
    }

    @Nested
    @DisplayName("reactivateUser")
    class ReactivateUser {

        @Test
        @DisplayName("비활성화된 유저를 재활성화하면 isActive가 true, deletedAt이 null이 된다")
        void reactivateUser_success() {
            mockUser.withdraw();
            when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));

            UserSummaryDTO result = adminUserService.reactivateUser(1L);

            assertThat(mockUser.getIsActive()).isTrue();
            assertThat(mockUser.getDeletedAt()).isNull();
            assertThat(result.isActive()).isTrue();
        }

        @Test
        @DisplayName("존재하지 않는 유저면 EntityNotFoundException을 던진다")
        void reactivateUser_throwsWhenUserNotFound() {
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> adminUserService.reactivateUser(99L))
                    .isInstanceOf(EntityNotFoundException.class);
        }

        @Test
        @DisplayName("이미 활성화된 유저면 ConflictException을 던진다")
        void reactivateUser_throwsWhenAlreadyActive() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));

            assertThatThrownBy(() -> adminUserService.reactivateUser(1L))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining(ErrorCode.USER_ALREADY_ACTIVE.getMessage());
        }
    }

    @Nested
    @DisplayName("deactivateUser")
    class DeactivateUser {

        @Test
        @DisplayName("연결된 Request가 없는 유저를 비활성화하면 isActive가 false, deletedAt은 null로 유지된다")
        void deactivateUser_withNoRequests_success() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
            when(requestRepository.findAllByUser(mockUser)).thenReturn(List.of());
            // 컨테이너는 이미 만료됐지만 지난 신청 이력에 노드가 남아 있어 삭제 범위를 좁힐 수 있다.
            when(requestRepository.findNodeNamesByUserIdOrderByRequestIdDesc(any())).thenReturn(List.of("farm1"));
            when(messageUtils.get(anyString(), any(Object[].class))).thenReturn("mock");

            UserSummaryDTO result = adminUserService.deactivateUser(1L);

            assertThat(mockUser.getIsActive()).isFalse();
            assertThat(mockUser.getDeletedAt()).isNull();
            assertThat(result.isActive()).isFalse();
            verifyNoInteractions(podService);
            // 비활성화도 삭제와 같이 리눅스 계정을 회수한다 (컨테이너는 이미 정리됨).
            verify(ubuntuAccountService).deleteUbuntuAccount("testuser", "farm1");
            verify(alarmService).sendAllAlerts(eq("홍길동"), eq("test@dgu.ac.kr"), anyString(), anyString());
        }

        @Test
        @DisplayName("존재하지 않는 유저면 EntityNotFoundException을 던진다")
        void deactivateUser_throwsWhenUserNotFound() {
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> adminUserService.deactivateUser(99L))
                    .isInstanceOf(EntityNotFoundException.class);
        }

        @Test
        @DisplayName("이미 비활성화된 유저면 ConflictException을 던진다")
        void deactivateUser_throwsWhenAlreadyInactive() {
            mockUser.withdraw();
            when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));

            assertThatThrownBy(() -> adminUserService.deactivateUser(1L))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining(ErrorCode.USER_ALREADY_INACTIVE.getMessage());
        }

        @Test
        @DisplayName("유저를 비활성화하면 남아있는 리프레시 토큰도 함께 폐기한다")
        void deactivateUser_revokesRefreshToken() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
            when(requestRepository.findAllByUser(mockUser)).thenReturn(List.of());
            // 컨테이너는 이미 만료됐지만 지난 신청 이력에 노드가 남아 있어 삭제 범위를 좁힐 수 있다.
            when(requestRepository.findNodeNamesByUserIdOrderByRequestIdDesc(any())).thenReturn(List.of("farm1"));
            when(messageUtils.get(anyString(), any(Object[].class))).thenReturn("mock");

            adminUserService.deactivateUser(1L);

            verify(tokenService).logout(1L);
        }

        @Test
        @DisplayName("FULFILLED 상태 Request가 있으면 deleteUser와 동일하게 외부 계정을 삭제한다")
        void deactivateUser_withFulfilledRequest_callsUbuntuDelete() {
            Request fulfilledRequest = mockFulfilledRequest("testuser", 1L);

            when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
            when(requestRepository.findAllByUser(mockUser)).thenReturn(List.of(fulfilledRequest));
            when(messageUtils.get(anyString(), any(Object[].class))).thenReturn("mock");

            adminUserService.deactivateUser(1L);

            verify(podService).deletePod("pod-testuser");
            verify(ubuntuAccountService).deleteUbuntuAccount("testuser", "farm1");
            verify(fulfilledRequest).deleteAfterCleanup();
            verify(alarmService).sendContainerDeletedEmail(fulfilledRequest);
            assertThat(mockUser.getIsActive()).isFalse();
            assertThat(mockUser.getDeletedAt()).isNull();
        }

        @Test
        @DisplayName("PENDING 상태 Request가 있으면 delete()를 호출한다 (외부 API 미호출)")
        void deactivateUser_withPendingRequest_callsDelete() {
            Request pendingRequest = mockRequestWithStatus(Status.PENDING);

            when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
            when(requestRepository.findAllByUser(mockUser)).thenReturn(List.of(pendingRequest));
            when(messageUtils.get(anyString(), any(Object[].class))).thenReturn("mock");

            adminUserService.deactivateUser(1L);

            verify(pendingRequest).delete();
            verifyNoInteractions(podService);
        }

        @Test
        @DisplayName("MIGRATING 상태 Request가 있으면 비활성화 자체를 거부하고 유저를 건드리지 않는다")
        void deactivateUser_withMigratingRequest_rejectsAndLeavesUserUntouched() {
            Request migrating = mockRequestWithStatus(Status.MIGRATING);

            when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
            when(requestRepository.findAllByUser(mockUser)).thenReturn(List.of(migrating));

            assertThatThrownBy(() -> adminUserService.deactivateUser(1L))
                    .isInstanceOf(ConflictException.class)
                    .extracting(e -> ((ConflictException) e).getErrorCode())
                    .isEqualTo(ErrorCode.REQUEST_MIGRATION_IN_PROGRESS);

            assertThat(mockUser.getIsActive()).isTrue();
            verifyNoInteractions(ubuntuAccountService, podService, tokenService);
        }

        @Test
        @DisplayName("PROCESSING 상태 Request가 있으면 비활성화 자체를 거부하고 유저를 건드리지 않는다")
        void deactivateUser_withProcessingRequest_rejectsAndLeavesUserUntouched() {
            Request processing = mockRequestWithStatus(Status.PROCESSING);

            when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
            when(requestRepository.findAllByUser(mockUser)).thenReturn(List.of(processing));

            assertThatThrownBy(() -> adminUserService.deactivateUser(1L))
                    .isInstanceOf(ConflictException.class)
                    .extracting(e -> ((ConflictException) e).getErrorCode())
                    .isEqualTo(ErrorCode.REQUEST_MIGRATION_IN_PROGRESS);

            assertThat(mockUser.getIsActive()).isTrue();
            verifyNoInteractions(ubuntuAccountService, podService, tokenService);
        }

        @Test
        @DisplayName("DELETED 상태 Request는 아무 처리도 하지 않는다")
        void deactivateUser_withDeletedRequest_skips() {
            Request deletedRequest = mockRequestWithStatus(Status.DELETED);

            when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
            when(requestRepository.findAllByUser(mockUser)).thenReturn(List.of(deletedRequest));
            when(messageUtils.get(anyString(), any(Object[].class))).thenReturn("mock");

            adminUserService.deactivateUser(1L);

            verify(deletedRequest, never()).delete();
            verify(deletedRequest, never()).deleteAfterCleanup();
            verifyNoInteractions(podService);
        }

        @Test
        @DisplayName("여러 상태의 Request가 혼합되면 각각 적절히 처리한다")
        void deactivateUser_withMixedRequests_handlesEachCorrectly() {
            Request fulfilled = mockFulfilledRequest("fuser", 10L);

            Request pending = mockRequestWithStatus(Status.PENDING);
            Request deleted = mockRequestWithStatus(Status.DELETED);

            when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
            when(requestRepository.findAllByUser(mockUser)).thenReturn(List.of(fulfilled, pending, deleted));
            when(messageUtils.get(anyString(), any(Object[].class))).thenReturn("mock");

            adminUserService.deactivateUser(1L);

            verify(podService).deletePod("pod-fuser");
            // 계정 삭제 대상은 요청이 아니라 웹 계정의 유저네임이고, 요청이 몇 개든 한 번만 부른다.
            verify(ubuntuAccountService, times(1)).deleteUbuntuAccount("testuser", "farm1");
            verify(fulfilled).deleteAfterCleanup();
            verify(alarmService).sendContainerDeletedEmail(fulfilled);
            verify(pending).delete();
            verify(deleted, never()).delete();
            verify(deleted, never()).deleteAfterCleanup();
            assertThat(mockUser.getIsActive()).isFalse();
        }

        @Test
        @DisplayName("FULFILLED 요청마다 인프라 삭제 직전에 행을 잠그고 EXPIRING으로 선점한다")
        void deactivateUser_locksAndClaimsEachRequestBeforeDeletingItsInfra() {
            Request req1 = mockFulfilledRequest("user1", 1L);
            Request req2 = mockFulfilledRequest("user2", 2L);
            Request req3 = mockFulfilledRequest("user3", 3L);

            when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
            when(requestRepository.findAllByUser(mockUser)).thenReturn(List.of(req1, req2, req3));
            when(messageUtils.get(anyString(), any(Object[].class))).thenReturn("mock");

            adminUserService.deactivateUser(1L);

            // 요청마다 선점(beginExpiry)이 그 요청의 Pod 삭제보다 먼저 일어나야, 사전 검사 이후
            // 새로 시작된 승인/마이그레이션이 실제로 차단된다.
            for (Request req : List.of(req1, req2, req3)) {
                InOrder order = inOrder(req, podService);
                order.verify(req).beginExpiry();
                order.verify(podService).deletePod("pod-" + req.getUbuntuUsername());
                verify(requestRepository, atLeastOnce()).findByIdForUpdate(req.getRequestId());
            }
            verify(requestRepository, never()).findById(anyLong());
            verify(alarmService, times(3)).sendContainerDeletedEmail(any(Request.class));
            verify(podService, times(3)).deletePod(anyString());
        }

        @Test
        @DisplayName("컨테이너 삭제 안내 메일 발송이 실패해도 계정 삭제와 비활성화는 계속 진행된다")
        void deactivateUser_continuesCleanup_whenContainerEmailFails() {
            Request req1 = mockFulfilledRequest("user1", 1L);
            Request req2 = mockFulfilledRequest("user2", 2L);

            when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
            when(requestRepository.findAllByUser(mockUser)).thenReturn(List.of(req1, req2));
            when(messageUtils.get(anyString(), any(Object[].class))).thenReturn("mock");
            doThrow(new RuntimeException("메일 서버 오류"))
                    .when(alarmService).sendContainerDeletedEmail(req1);

            adminUserService.deactivateUser(1L);

            verify(podService).deletePod("pod-user1");
            verify(podService).deletePod("pod-user2");
            // 요청이 두 개여도 리눅스 계정은 하나뿐이라 계정 삭제는 마지막에 한 번만 일어난다.
            verify(ubuntuAccountService, times(1)).deleteUbuntuAccount("testuser", "farm1");
            verify(req1).deleteAfterCleanup();
            verify(req2).deleteAfterCleanup();
            verify(alarmService).sendContainerDeletedEmail(req2);
            assertThat(mockUser.getIsActive()).isFalse();
        }

        @Test
        @DisplayName("최종 비활성화 안내 메일 발송이 실패해도 비활성화 자체는 완료된다")
        void deactivateUser_completes_whenFinalNotificationEmailFails() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
            when(requestRepository.findAllByUser(mockUser)).thenReturn(List.of());
            // 컨테이너는 이미 만료됐지만 지난 신청 이력에 노드가 남아 있어 삭제 범위를 좁힐 수 있다.
            when(requestRepository.findNodeNamesByUserIdOrderByRequestIdDesc(any())).thenReturn(List.of("farm1"));
            when(messageUtils.get(anyString(), any(Object[].class))).thenReturn("mock");
            doThrow(new RuntimeException("Slack/메일 발송 실패"))
                    .when(alarmService).sendAllAlerts(anyString(), anyString(), anyString(), anyString());

            UserSummaryDTO result = adminUserService.deactivateUser(1L);

            assertThat(mockUser.getIsActive()).isFalse();
            assertThat(result.isActive()).isFalse();
            verify(tokenService).logout(1L);
        }
    }

    @Nested
    @DisplayName("changeUserRole")
    class ChangeUserRole {

        @Test
        @DisplayName("USER를 ADMIN으로 변경한다")
        void changeUserRole_userToAdmin_success() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));

            UserSummaryDTO result = adminUserService.changeUserRole(1L, Role.ADMIN);

            assertThat(mockUser.getRole()).isEqualTo(Role.ADMIN);
            assertThat(result.role()).isEqualTo("ADMIN");
        }

        @Test
        @DisplayName("존재하지 않는 유저면 EntityNotFoundException을 던진다")
        void changeUserRole_throwsWhenUserNotFound() {
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> adminUserService.changeUserRole(99L, Role.ADMIN))
                    .isInstanceOf(EntityNotFoundException.class);
        }

        @Test
        @DisplayName("이미 같은 권한이면 ConflictException을 던진다")
        void changeUserRole_throwsWhenAlreadyHasRole() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));

            assertThatThrownBy(() -> adminUserService.changeUserRole(1L, Role.USER))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining(ErrorCode.USER_ALREADY_HAS_ROLE.getMessage());
        }
    }

    @Nested
    @DisplayName("deleteUbuntuAccount (단독 엔드포인트용)")
    class DeleteUbuntuAccount {

        @Test
        @DisplayName("FULFILLED Request가 있으면 외부 API 호출 후 DB 상태를 DELETED로 변경하고 계정을 회수한다")
        void deleteUbuntuAccount_success() {
            Request request = mockFulfilledRequest("testuser", 50L);
            when(userRepository.findByUbuntuUsername("testuser")).thenReturn(Optional.of(mockUser));
            when(requestRepository.findAllByUser(mockUser)).thenReturn(List.of(request));

            adminUserService.deleteUbuntuAccount("testuser");

            verify(podService).deletePod("pod-testuser");
            verify(ubuntuAccountService).deleteUbuntuAccount("testuser", "farm1");
            verify(request).deleteAfterCleanup();
            verify(alarmService).sendContainerDeletedEmail(request);
            assertThat(mockUser.hasUbuntuAccount()).isFalse();
        }

        @Test
        @DisplayName("서로 다른 노드에 신청을 여러 개 가지고 있으면 전부 정리하고 각 노드에서 계정을 회수한다 — 가장 최근 신청 하나만 지우면 다른 노드에 UID 없는 컨테이너가 남는다")
        void deleteUbuntuAccount_withLiveRequestsOnDifferentNodes_cleansUpAllAndReleasesEachNode() {
            Request onFarm1 = mockFulfilledRequest("testuser", 51L, "farm1");
            Request onFarm2 = mockFulfilledRequest("testuser", 52L, "farm2");
            when(userRepository.findByUbuntuUsername("testuser")).thenReturn(Optional.of(mockUser));
            when(requestRepository.findAllByUser(mockUser)).thenReturn(List.of(onFarm1, onFarm2));

            adminUserService.deleteUbuntuAccount("testuser");

            verify(ubuntuAccountService).deleteUbuntuAccount("testuser", "farm1");
            verify(ubuntuAccountService).deleteUbuntuAccount("testuser", "farm2");
            verify(onFarm1).deleteAfterCleanup();
            verify(onFarm2).deleteAfterCleanup();
            assertThat(mockUser.hasUbuntuAccount()).isFalse();
        }

        @Test
        @DisplayName("승인/마이그레이션 진행 중인 요청이 있으면 정리 자체를 거부한다 — 기존에는 그 요청만 걸러서 나머지를 지울 수 있었다")
        void deleteUbuntuAccount_refusesInFlightRequest() {
            Request migrating = mockRequestWithStatus(Status.MIGRATING);
            when(userRepository.findByUbuntuUsername("testuser")).thenReturn(Optional.of(mockUser));
            when(requestRepository.findAllByUser(mockUser)).thenReturn(List.of(migrating));

            assertThatThrownBy(() -> adminUserService.deleteUbuntuAccount("testuser"))
                    .isInstanceOf(ConflictException.class)
                    .extracting(e -> ((ConflictException) e).getErrorCode())
                    .isEqualTo(ErrorCode.REQUEST_MIGRATION_IN_PROGRESS);

            verifyNoInteractions(ubuntuAccountService, podService);
            assertThat(mockUser.hasUbuntuAccount()).isTrue();
        }

        @Test
        @DisplayName("외부 삭제가 실패하면 그 요청은 FULFILLED로 되돌리고, 계정은 지우지 않는다")
        void deleteUbuntuAccount_revertsToFulfilledOnFailure() {
            Request request = mockFulfilledRequest("testuser", 53L);
            when(userRepository.findByUbuntuUsername("testuser")).thenReturn(Optional.of(mockUser));
            when(requestRepository.findAllByUser(mockUser)).thenReturn(List.of(request));
            doThrow(new RuntimeException("config-server 통신 오류")).when(podService).deletePod("pod-testuser");

            assertThatThrownBy(() -> adminUserService.deleteUbuntuAccount("testuser"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.USER_REQUEST_CLEANUP_PARTIALLY_FAILED);

            assertThat(request.getStatus()).isEqualTo(Status.FULFILLED);
            verifyNoInteractions(ubuntuAccountService);
            assertThat(mockUser.hasUbuntuAccount()).isTrue();
        }

        @Test
        @DisplayName("해당 유저네임의 계정이 없으면 EntityNotFoundException을 던진다")
        void deleteUbuntuAccount_throwsWhenNotFound() {
            when(userRepository.findByUbuntuUsername("nobody")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> adminUserService.deleteUbuntuAccount("nobody"))
                    .isInstanceOf(EntityNotFoundException.class);

            verifyNoInteractions(ubuntuAccountService, podService);
        }
    }

    /**
     * deleteUser/deactivateUser/deleteUbuntuAccount 셋 다 cleanupUserRequests를 공유한다.
     * 세 진입점 × 노드 분포(단일/서로 다른 두 노드/한 노드 중복) × 비활성 요청(PENDING·DELETED)
     * 혼재 여부를 카타시안 곱으로 조합해, 어느 진입점으로 들어와도 살아있는 요청의 노드를
     * 빠짐없이·중복없이 순회해 계정을 회수하는지 한 번에 검증한다.
     */
    @Nested
    @DisplayName("cleanupUserRequests 공유 로직 — 진입점 × 노드 분포 카타시안 곱")
    class CleanupNodeMatrix {

        private enum EntryPoint { DELETE_USER, DEACTIVATE_USER, DELETE_UBUNTU_ACCOUNT }

        private enum NodeDistribution {
            SINGLE_NODE(List.of("farm1")),
            TWO_DISTINCT_NODES(List.of("farm1", "farm2")),
            DUPLICATE_NODE(List.of("farm1", "farm1", "farm2"));

            private final List<String> nodes;

            NodeDistribution(List<String> nodes) {
                this.nodes = nodes;
            }
        }

        static Stream<Arguments> matrix() {
            List<Arguments> combinations = new ArrayList<>();
            for (EntryPoint entryPoint : EntryPoint.values()) {
                for (NodeDistribution nodeDistribution : NodeDistribution.values()) {
                    for (boolean withExtraNonLiveRequests : List.of(false, true)) {
                        combinations.add(Arguments.of(entryPoint, nodeDistribution, withExtraNonLiveRequests));
                    }
                }
            }
            return combinations.stream();
        }

        @ParameterizedTest(name = "[{index}] entry={0}, nodes={1}, extraNonLive={2}")
        @MethodSource("matrix")
        @DisplayName("모든 진입점이 살아있는 요청의 노드를 빠짐없이·중복없이 순회해 계정을 회수한다")
        void cleanupReleasesAccountOnEveryDistinctNodeRegardlessOfEntryPoint(
                EntryPoint entryPoint, NodeDistribution nodeDistribution, boolean withExtraNonLiveRequests) {

            List<Request> liveRequests = new ArrayList<>();
            long requestId = 900L;
            for (String node : nodeDistribution.nodes) {
                liveRequests.add(mockFulfilledRequest("testuser", requestId++, node));
            }
            List<Request> allRequests = new ArrayList<>(liveRequests);
            Request pending = null;
            Request deleted = null;
            if (withExtraNonLiveRequests) {
                pending = mockRequestWithStatus(Status.PENDING);
                deleted = mockRequestWithStatus(Status.DELETED);
                allRequests.add(pending);
                allRequests.add(deleted);
            }

            lenient().when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
            lenient().when(userRepository.findByUbuntuUsername("testuser")).thenReturn(Optional.of(mockUser));
            when(requestRepository.findAllByUser(mockUser)).thenReturn(allRequests);
            lenient().when(messageUtils.get(anyString(), any(Object[].class))).thenReturn("mock");

            switch (entryPoint) {
                case DELETE_USER -> adminUserService.deleteUser(1L);
                case DEACTIVATE_USER -> adminUserService.deactivateUser(1L);
                case DELETE_UBUNTU_ACCOUNT -> adminUserService.deleteUbuntuAccount("testuser");
            }

            // 노드 분포에 몇 개가 중복됐든, 실제로 지워야 할 서로 다른 노드 각각에 정확히
            // 한 번씩만 호출돼야 한다 — 이게 이번에 고친 두 버그의 핵심 불변식이다.
            Set<String> expectedNodes = new LinkedHashSet<>(nodeDistribution.nodes);
            for (String node : expectedNodes) {
                verify(ubuntuAccountService).deleteUbuntuAccount("testuser", node);
            }
            verify(ubuntuAccountService, times(expectedNodes.size())).deleteUbuntuAccount(anyString(), anyString());

            for (Request live : liveRequests) {
                verify(live).deleteAfterCleanup();
            }
            if (withExtraNonLiveRequests) {
                verify(pending).delete();
                verify(deleted, never()).delete();
                verify(deleted, never()).deleteAfterCleanup();
            }
            assertThat(mockUser.hasUbuntuAccount()).isFalse();
        }
    }
}
