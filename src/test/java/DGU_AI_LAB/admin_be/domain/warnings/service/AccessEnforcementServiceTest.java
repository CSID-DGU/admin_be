package DGU_AI_LAB.admin_be.domain.warnings.service;

import DGU_AI_LAB.admin_be.domain.alarm.service.AlarmService;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.AccessChangeRegisterRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.job.JobClient;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import DGU_AI_LAB.admin_be.domain.warnings.entity.AccessOperation;
import DGU_AI_LAB.admin_be.domain.warnings.entity.AccessOperationStatus;
import DGU_AI_LAB.admin_be.domain.warnings.entity.UserSuspension;
import DGU_AI_LAB.admin_be.domain.warnings.repository.AccessOperationRepository;
import DGU_AI_LAB.admin_be.domain.warnings.repository.UserSuspensionRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import DGU_AI_LAB.admin_be.global.util.MessageUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AccessEnforcementServiceTest {

    private static final Long USER_ID = 5L;
    private static final Long OPERATION_ID = 7L;
    private static final Long JOB_ID = 77L;

    @Mock private AccessOperationRepository operationRepository;
    @Mock private UserSuspensionRepository suspensionRepository;
    @Mock private UserRepository userRepository;
    @Mock private JobClient jobClient;
    @Mock private AlarmService alarmService;
    @Mock private MessageUtils messageUtils;
    @Mock private PlatformTransactionManager transactionManager;
    @Mock private TransactionStatus transactionStatus;

    private AccessEnforcementService service;
    private User user;
    private AtomicLong operationIds;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        service = new AccessEnforcementService(operationRepository, suspensionRepository, userRepository, jobClient,
                alarmService, messageUtils, transactionManager);

        user = User.builder().email("alice@dgu.ac.kr").name("alice").ubuntuUsername("alice").build();
        ReflectionTestUtils.setField(user, "userId", USER_ID);
        user.assignUbuntuAccount("FARM", 10_001L, 10_001L);
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
        // 저장하면 번호가 붙는다(IDENTITY).
        operationIds = new AtomicLong(OPERATION_ID);
        when(operationRepository.save(any(AccessOperation.class))).thenAnswer(invocation -> {
            AccessOperation saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "accessOperationId", operationIds.getAndIncrement());
            return saved;
        });
        when(jobClient.registerAccessChange(any(String.class), any())).thenReturn(JOB_ID);
        when(messageUtils.get(any(), any())).thenReturn("문구");
    }

    private void suspended(boolean suspended) {
        when(suspensionRepository.findFirstByUser_UserIdAndEndsAtAfterOrderByEndsAtDesc(eq(USER_ID), any()))
                .thenReturn(suspended ? Optional.of(mock(UserSuspension.class)) : Optional.empty());
    }

    /** 마지막으로 성공한 작업이 차단이었는지. */
    private void applied(boolean blocked) {
        applied("FARM", "alice", blocked);
    }

    private AccessOperation applied(String clusterId, String username, boolean blocked) {
        AccessOperation last = new AccessOperation(user, clusterId, username, blocked);
        last.markApplied();
        when(operationRepository.findFirstByUser_UserIdAndClusterIdAndStatusOrderByAccessOperationIdDesc(
                USER_ID, clusterId, AccessOperationStatus.APPLIED)).thenReturn(Optional.of(last));
        return last;
    }

    private AccessOperation processing(boolean blocked) {
        return processing(OPERATION_ID, "FARM", "alice", blocked);
    }

    private AccessOperation processing(Long operationId, String clusterId, String username, boolean blocked) {
        AccessOperation operation = new AccessOperation(user, clusterId, username, blocked);
        ReflectionTestUtils.setField(operation, "accessOperationId", operationId);
        operation.registered(JOB_ID);
        when(operationRepository.findByIdForUpdate(operationId)).thenReturn(Optional.of(operation));
        return operation;
    }

    @Test
    @DisplayName("정지 중인데 접속이 열려 있으면 계정 단위 차단 작업을 등록한다")
    void blocksWhenSuspendedAndOpen() {
        suspended(true);

        service.enforce(USER_ID);

        verify(jobClient).registerAccessChange("FARM", new AccessChangeRegisterRequestDTO(OPERATION_ID, "alice", true));
    }

    @Test
    @DisplayName("정지가 끝났는데 접속이 막혀 있으면 해제 작업을 등록한다")
    void unblocksWhenSuspensionEnded() {
        suspended(false);
        applied(true);

        service.enforce(USER_ID);

        verify(jobClient).registerAccessChange("FARM", new AccessChangeRegisterRequestDTO(OPERATION_ID, "alice", false));
    }

    @Test
    @DisplayName("이미 맞춰져 있으면 아무 작업도 등록하지 않는다")
    void doesNothingWhenAlreadyAligned() {
        suspended(true);
        applied(true);
        service.enforce(USER_ID);

        suspended(false);
        applied(false);
        service.enforce(USER_ID);

        verifyNoInteractions(jobClient);
    }

    @Test
    @DisplayName("정지 이력이 없는 사용자는 건드리지 않는다")
    void neverSuspendedUserIsLeftAlone() {
        suspended(false);

        service.enforce(USER_ID);

        verifyNoInteractions(jobClient);
    }

    @Test
    @DisplayName("작업이 도는 중이면 새 작업을 등록하지 않는다 — 끝난 뒤 다시 맞춘다")
    void waitsForRunningOperation() {
        suspended(true);
        when(operationRepository.existsByUser_UserIdAndClusterIdAndStatus(
                USER_ID, "FARM", AccessOperationStatus.PROCESSING)).thenReturn(true);

        service.enforce(USER_ID);

        verifyNoInteractions(jobClient);
    }

    @Test
    @DisplayName("다시 걸기는 차단 작업이 도는 중이어도 등록한다 — 도는 작업은 방금 만들어진 포트를 못 봤을 수 있다")
    void reapplyOverlapsRunningBlock() {
        suspended(true);
        when(operationRepository.existsByUser_UserIdAndClusterIdAndStatus(
                USER_ID, "FARM", AccessOperationStatus.PROCESSING)).thenReturn(true);
        when(operationRepository.existsByUser_UserIdAndClusterIdAndStatusAndBlocked(
                USER_ID, "FARM", AccessOperationStatus.PROCESSING, false))
                .thenReturn(false);

        service.reapply(USER_ID);

        verify(jobClient).registerAccessChange("FARM", new AccessChangeRegisterRequestDTO(OPERATION_ID, "alice", true));
    }

    @Test
    @DisplayName("다시 걸기도 해제 작업이 도는 중이면 기다린다 — 순서가 뒤바뀌면 열린 채로 끝난다")
    void reapplyWaitsForRunningUnblock() {
        suspended(true);
        when(operationRepository.existsByUser_UserIdAndClusterIdAndStatus(
                USER_ID, "FARM", AccessOperationStatus.PROCESSING)).thenReturn(true);
        when(operationRepository.existsByUser_UserIdAndClusterIdAndStatusAndBlocked(
                USER_ID, "FARM", AccessOperationStatus.PROCESSING, false))
                .thenReturn(true);

        service.reapply(USER_ID);

        verifyNoInteractions(jobClient);
    }

    @Test
    @DisplayName("리눅스 계정을 정한 적이 없는 사용자는 막을 포트가 없어 작업을 등록하지 않는다")
    void userWithoutLinuxAccountHasNothingToBlock() {
        user.getUbuntuAccounts().clear();
        ReflectionTestUtils.setField(user, "ubuntuUsername", null);
        suspended(true);

        service.enforce(USER_ID);

        verifyNoInteractions(jobClient);
    }

    @Test
    @DisplayName("다시 걸기는 이미 막혀 있어도 차단 작업을 한 번 더 등록한다")
    void reapplyBlocksAgainEvenWhenAlreadyBlocked() {
        suspended(true);
        applied(true);

        service.reapply(USER_ID);

        verify(jobClient).registerAccessChange("FARM", new AccessChangeRegisterRequestDTO(OPERATION_ID, "alice", true));
    }

    @Test
    @DisplayName("다시 걸기는 정지 중이 아니면 차단하지 않는다")
    void reapplyDoesNotBlockWhenNotSuspended() {
        suspended(false);

        service.reapply(USER_ID);

        verifyNoInteractions(jobClient);
    }

    @Test
    @DisplayName("등록이 실패하면 예외를 올린다 — 호출자가 다음 점검에 맡긴다")
    void registrationFailurePropagates() {
        suspended(true);
        when(jobClient.registerAccessChange(any(String.class), any())).thenThrow(new BusinessException(ErrorCode.ACCESS_CHANGE_FAILED));

        assertThatThrownBy(() -> service.enforce(USER_ID)).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("FARM/LAB 활성 계정은 각 클러스터 유저네임으로 별도 차단 작업을 등록한다")
    void fansOutAccessChangeToEveryActiveClusterAccount() {
        user.assignUbuntuAccount("LAB", 20_001L, 20_001L);
        ReflectionTestUtils.setField(user.ubuntuAccount("LAB").orElseThrow(), "username", "alice-lab");
        suspended(true);

        service.enforce(USER_ID);

        verify(jobClient).registerAccessChange("FARM",
                new AccessChangeRegisterRequestDTO(OPERATION_ID, "alice", true));
        verify(jobClient).registerAccessChange("LAB",
                new AccessChangeRegisterRequestDTO(OPERATION_ID + 1, "alice-lab", true));
    }

    @Test
    @DisplayName("두 번째 클러스터 등록이 실패해도 첫 클러스터 등록 트랜잭션은 이미 커밋되어 추적 행을 잃지 않는다")
    void clusterRegistrationsUseIndependentTransactions() {
        user.assignUbuntuAccount("LAB", 20_001L, 20_001L);
        ReflectionTestUtils.setField(user.ubuntuAccount("LAB").orElseThrow(), "username", "alice-lab");
        suspended(true);
        doAnswer(invocation -> {
            String clusterId = invocation.getArgument(0);
            if ("LAB".equals(clusterId)) {
                throw new BusinessException(ErrorCode.ACCESS_CHANGE_FAILED);
            }
            return JOB_ID;
        }).when(jobClient).registerAccessChange(any(String.class), any());

        assertThatThrownBy(() -> service.enforce(USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.ACCESS_CHANGE_FAILED);

        verify(jobClient).registerAccessChange("FARM",
                new AccessChangeRegisterRequestDTO(OPERATION_ID, "alice", true));
        verify(jobClient).registerAccessChange("LAB",
                new AccessChangeRegisterRequestDTO(OPERATION_ID + 1, "alice-lab", true));
        // 활성 서버 목록 조회 + FARM 등록은 커밋되고, 별도 LAB 등록만 롤백된다.
        verify(transactionManager, times(2)).commit(transactionStatus);
        verify(transactionManager).rollback(transactionStatus);
    }

    @Test
    @DisplayName("해제가 성공하면 사용자에게 정지 종료를 알린다")
    void completedUnblockNotifiesUser() {
        AccessOperation operation = processing(false);
        suspended(false);

        service.complete(OPERATION_ID);

        assertThat(operation.getStatus()).isEqualTo(AccessOperationStatus.APPLIED);
        verify(alarmService).notifyUser(eq("alice"), eq("alice@dgu.ac.kr"), any(), any());
    }

    @Test
    @DisplayName("FARM/LAB 해제가 모두 성공한 뒤 마지막 완료에서만 정지 종료를 한 번 알린다")
    void completedUnblockNotifiesOnceAfterEveryClusterApplied() {
        user.assignUbuntuAccount("LAB", 20_001L, 20_001L);
        ReflectionTestUtils.setField(user.ubuntuAccount("LAB").orElseThrow(), "username", "alice-lab");
        AccessOperation farm = processing(OPERATION_ID, "FARM", "alice", false);
        AccessOperation lab = processing(OPERATION_ID + 1, "LAB", "alice-lab", false);
        AccessOperation oldLabBlocked = new AccessOperation(user, "LAB", "alice-lab", true);
        oldLabBlocked.markApplied();
        suspended(false);

        when(operationRepository.existsByUser_UserIdAndClusterIdAndStatus(
                eq(USER_ID), any(String.class), eq(AccessOperationStatus.PROCESSING)))
                .thenAnswer(invocation -> {
                    String cluster = invocation.getArgument(1);
                    return "FARM".equals(cluster) ? farm.isProcessing() : lab.isProcessing();
                });
        when(operationRepository.findFirstByUser_UserIdAndClusterIdAndStatusOrderByAccessOperationIdDesc(
                eq(USER_ID), any(String.class), eq(AccessOperationStatus.APPLIED)))
                .thenAnswer(invocation -> {
                    String cluster = invocation.getArgument(1);
                    if ("FARM".equals(cluster)) {
                        return farm.getStatus() == AccessOperationStatus.APPLIED
                                ? Optional.of(farm) : Optional.empty();
                    }
                    return lab.getStatus() == AccessOperationStatus.APPLIED
                            ? Optional.of(lab) : Optional.of(oldLabBlocked);
                });

        service.complete(OPERATION_ID);
        verifyNoInteractions(alarmService);

        service.complete(OPERATION_ID + 1);
        verify(alarmService, times(1)).notifyUser(eq("alice"), eq("alice@dgu.ac.kr"), any(), any());
    }

    @Test
    @DisplayName("차단이 성공해도 따로 알리지 않는다 — 경고를 줄 때 이미 알렸다")
    void completedBlockDoesNotNotify() {
        AccessOperation operation = processing(true);
        suspended(true);
        applied(true);

        service.complete(OPERATION_ID);

        assertThat(operation.getStatus()).isEqualTo(AccessOperationStatus.APPLIED);
        verifyNoInteractions(alarmService);
    }

    @Test
    @DisplayName("차단 작업이 도는 사이 경고가 취소됐으면 끝나자마자 해제 작업을 등록한다")
    void completedBlockIsFollowedByUnblockWhenSuspensionWasLifted() {
        processing(true);
        suspended(false);
        applied(true);

        service.complete(OPERATION_ID);

        verify(jobClient).registerAccessChange("FARM", new AccessChangeRegisterRequestDTO(OPERATION_ID, "alice", false));
    }

    @Test
    @DisplayName("이미 끝난 작업의 결과는 다시 반영하지 않는다")
    void finishedOperationIsNotAppliedTwice() {
        AccessOperation operation = processing(true);
        operation.markFailed("X");

        service.complete(OPERATION_ID);
        service.fail(OPERATION_ID, "Y");

        assertThat(operation.getErrorCode()).isEqualTo("X");
        verifyNoInteractions(alarmService, jobClient);
    }

    @Test
    @DisplayName("작업이 실패하면 관리자에게 알리고 긴 오류 코드는 칸에 맞게 자른다")
    void failureAlertsAdmin() {
        AccessOperation operation = processing(true);

        service.fail(OPERATION_ID, "E".repeat(100));

        assertThat(operation.getStatus()).isEqualTo(AccessOperationStatus.FAILED);
        assertThat(operation.getErrorCode()).hasSize(64);
        verify(alarmService).alertNeedsAction(eq("notification.admin.access.change-failed"),
                eq("차단"), eq("alice"), eq(OPERATION_ID), any());
    }

    @Test
    @DisplayName("주기 점검은 정지 중인 사용자와 막혀 있는 사용자를 모두 보고, 한 사용자의 실패가 나머지를 막지 않는다")
    void periodicCheckCoversSuspendedAndBlockedUsers() {
        when(suspensionRepository.findSuspendedUserIds(any())).thenReturn(List.of(1L, USER_ID));
        when(operationRepository.findBlockedUserIds(AccessOperationStatus.APPLIED)).thenReturn(List.of(USER_ID, 9L));
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.empty());
        when(userRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(user));
        suspended(true);

        service.enforceAll();

        verify(userRepository, times(1)).findByIdForUpdate(1L);
        // 활성 서버 목록을 읽는 트랜잭션과 그 서버 작업을 등록하는 독립 트랜잭션에서 각각 잠근다.
        verify(userRepository, times(2)).findByIdForUpdate(USER_ID);
        verify(userRepository, times(2)).findByIdForUpdate(9L);
        verify(jobClient, never()).registerAccessChange("FARM", new AccessChangeRegisterRequestDTO(OPERATION_ID, "alice", false));
    }

    @Test
    @DisplayName("주기 점검은 정지 중인 사용자를 이미 막혀 있다고 기록돼 있어도 다시 막는다")
    void periodicCheckReblocksSuspendedUser() {
        when(suspensionRepository.findSuspendedUserIds(any())).thenReturn(List.of(USER_ID));
        when(operationRepository.findBlockedUserIds(AccessOperationStatus.APPLIED)).thenReturn(List.of(USER_ID));
        suspended(true);
        applied(true);

        service.enforceAll();

        verify(jobClient).registerAccessChange("FARM", new AccessChangeRegisterRequestDTO(OPERATION_ID, "alice", true));
    }
}
