package DGU_AI_LAB.admin_be.domain.groups.service;

import DGU_AI_LAB.admin_be.support.Alerts;
import DGU_AI_LAB.admin_be.domain.alarm.service.AlarmService;
import DGU_AI_LAB.admin_be.domain.groups.dto.response.GroupOperationResponseDTO;
import DGU_AI_LAB.admin_be.domain.groups.entity.Group;
import DGU_AI_LAB.admin_be.domain.groups.entity.GroupOperation;
import DGU_AI_LAB.admin_be.domain.groups.entity.GroupOperationKind;
import DGU_AI_LAB.admin_be.domain.groups.entity.GroupOperationStatus;
import DGU_AI_LAB.admin_be.domain.groups.repository.GroupOperationRepository;
import DGU_AI_LAB.admin_be.domain.groups.repository.GroupRepository;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.GroupChangeRegisterRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.JobResultResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.entity.ChangeRequest;
import DGU_AI_LAB.admin_be.domain.requests.entity.ChangeType;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.job.JobClient;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GroupOperationServiceTest {

    private static final Long USER_ID = 5L;
    private static final Long ADMIN_ID = 1L;
    private static final Long OPERATION_ID = 7L;
    private static final Long JOB_ID = 77L;

    @Mock private GroupOperationRepository operationRepository;
    @Mock private GroupRepository groupRepository;
    @Mock private UserRepository userRepository;
    @Mock private RequestRepository requestRepository;
    @Mock private ChangeRequestRepository changeRequestRepository;
    @Mock private JobClient jobClient;
    @Mock private AlarmService alarmService;
    @Mock private PlatformTransactionManager transactionManager;
    @Mock private TransactionStatus transactionStatus;

    private GroupOperationService service;
    private User user;
    private User admin;
    private Group teamx;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        service = new GroupOperationService(operationRepository, groupRepository, userRepository, requestRepository,
                changeRequestRepository, jobClient, alarmService, new ObjectMapper(),
                transactionManager);

        user = user(USER_ID, "alice");
        admin = user(ADMIN_ID, "root-admin");
        teamx = group(3L, "teamx", 70000L);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userRepository.getReferenceById(ADMIN_ID)).thenReturn(admin);
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
        when(groupRepository.findById(3L)).thenReturn(Optional.of(teamx));
        // 저장하면 번호가 붙는다(IDENTITY).
        when(operationRepository.save(any(GroupOperation.class))).thenAnswer(invocation -> {
            GroupOperation saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "groupOperationId", OPERATION_ID);
            return saved;
        });
        when(jobClient.registerGroupChange(any())).thenReturn(JOB_ID);
    }

    private static User user(Long id, String ubuntuUsername) {
        User u = User.builder().email(ubuntuUsername + "@dgu.ac.kr").name(ubuntuUsername).ubuntuUsername(ubuntuUsername).build();
        ReflectionTestUtils.setField(u, "userId", id);
        return u;
    }

    private static Group group(Long id, String name, Long gid) {
        Group g = Group.builder().groupName(name).ubuntuGid(gid).build();
        ReflectionTestUtils.setField(g, "groupId", id);
        return g;
    }

    /** 반영 중인 작업을 저장소에 둔다. */
    private GroupOperation processing(GroupOperation operation) {
        ReflectionTestUtils.setField(operation, "groupOperationId", OPERATION_ID);
        operation.registered(JOB_ID);
        when(operationRepository.findByIdForUpdate(OPERATION_ID)).thenReturn(Optional.of(operation));
        when(operationRepository.findById(OPERATION_ID)).thenReturn(Optional.of(operation));
        return operation;
    }

    private GroupChangeRegisterRequestDTO registered() {
        ArgumentCaptor<GroupChangeRegisterRequestDTO> captor = ArgumentCaptor.forClass(GroupChangeRegisterRequestDTO.class);
        verify(jobClient).registerGroupChange(captor.capture());
        return captor.getValue();
    }

    private static JobResultResponseDTO.Result gid(Long gid) {
        return new JobResultResponseDTO.Result(null, gid, null, null, null);
    }

    // 그룹 생성은 이제 DB 에만 한다(GroupServiceTest). 이 변경 전에 등록돼 아직 도는 생성 작업의 결과 반영만 남았다.
    @Nested
    @DisplayName("그룹 생성 — 이 변경 전에 등록된 작업의 결과 반영")
    class Create {

        @Test
        @DisplayName("작업이 성공하면 결과의 gid 로 그룹을 저장한다")
        void successSavesTheGroupWithTheAssignedGid() {
            GroupOperation operation = processing(GroupOperation.create(user, "teamy", null));

            service.complete(OPERATION_ID, gid(70001L));

            ArgumentCaptor<Group> saved = ArgumentCaptor.forClass(Group.class);
            verify(groupRepository).save(saved.capture());
            assertThat(saved.getValue().getGroupName()).isEqualTo("teamy");
            assertThat(saved.getValue().getUbuntuGid()).isEqualTo(70001L);
            assertThat(operation.getStatus()).isEqualTo(GroupOperationStatus.APPLIED);
        }

        @Test
        @DisplayName("결과에 gid 가 없거나 같은 그룹이 먼저 저장됐으면 저장하지 않고 실패로 남긴다")
        void unusableResultFailsWithoutSaving() {
            GroupOperation noGid = processing(GroupOperation.create(user, "teamy", null));
            service.complete(OPERATION_ID, gid(null));
            assertThat(noGid.getStatus()).isEqualTo(GroupOperationStatus.FAILED);
            assertThat(noGid.getErrorCode()).isEqualTo(GroupOperationService.ERROR_GID_MISSING);

            GroupOperation duplicate = processing(GroupOperation.create(user, "teamy", null));
            when(groupRepository.existsByGroupName("teamy")).thenReturn(true);
            service.complete(OPERATION_ID, gid(70001L));
            assertThat(duplicate.getStatus()).isEqualTo(GroupOperationStatus.FAILED);
            assertThat(duplicate.getErrorCode()).isEqualTo(GroupOperationService.ERROR_DUPLICATE_GROUP);

            verify(groupRepository, never()).save(any());
        }

        @Test
        @DisplayName("작업이 실패하면 오류 코드를 남기고 그룹은 만들지 않는다")
        void failureKeepsTheErrorCode() {
            GroupOperation operation = processing(GroupOperation.create(user, "teamy", null));

            service.fail(OPERATION_ID, "AD_GROUP_CREATE_FAILED");

            assertThat(operation.getStatus()).isEqualTo(GroupOperationStatus.FAILED);
            assertThat(operation.getErrorCode()).isEqualTo("AD_GROUP_CREATE_FAILED");
            verify(groupRepository, never()).save(any());
            verifyNoInteractions(alarmService);
        }
    }

    @Nested
    @DisplayName("그룹 추가(변경 요청 승인)")
    class Add {

        private ChangeRequest changeRequest;
        private Request originalRequest;

        @BeforeEach
        void setUp() {
            ResourceGroup resourceGroup = mock(ResourceGroup.class);
            when(resourceGroup.getServerName()).thenReturn("FARM");
            originalRequest = mock(Request.class);
            when(originalRequest.getUser()).thenReturn(user);
            when(originalRequest.getUbuntuUsername()).thenReturn("alice");
            when(originalRequest.getResourceGroup()).thenReturn(resourceGroup);
            changeRequest = ChangeRequest.builder().request(originalRequest).changeType(ChangeType.GROUP)
                    .newValue("[70000]").reason("팀 합류").requestedBy(user).build();
            ReflectionTestUtils.setField(changeRequest, "changeRequestId", 9L);
            when(changeRequestRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(changeRequest));
            when(groupRepository.findAllByUbuntuGidIn(any())).thenReturn(List.of(teamx));
        }

        @Test
        @DisplayName("승인하면 작업을 등록하고 변경 요청을 반영 중으로 둔다 — 사용자 그룹은 아직 그대로다")
        void approvalRegistersJobAndMarksProcessing() {
            service.startAdd(changeRequest, originalRequest, admin, "승인합니다");

            assertThat(registered()).isEqualTo(GroupChangeRegisterRequestDTO.add(OPERATION_ID, "alice", List.of("teamx")));
            assertThat(changeRequest.getStatus()).isEqualTo(Status.PROCESSING);
            assertThat(changeRequest.getAdminComment()).isEqualTo("승인합니다");
            assertThat(user.getUserGroups()).isEmpty();
        }

        @Test
        @DisplayName("없는 그룹이 섞여 있으면 등록하지 않는다")
        void unknownGroupIsNotRegistered() {
            when(groupRepository.findAllByUbuntuGidIn(any())).thenReturn(List.of());

            assertThatThrownBy(() -> service.startAdd(changeRequest, originalRequest, admin, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
            verifyNoInteractions(jobClient);
            assertThat(changeRequest.getStatus()).isEqualTo(Status.PENDING);
        }

        @Test
        @DisplayName("작업이 성공하면 계정에 그룹을 기록하고 승인을 끝낸 뒤 안내 메일을 보낸다")
        void successRecordsGroupsAndCompletesApproval() {
            changeRequest.startProcessing(admin, "승인합니다");
            GroupOperation operation = processing(GroupOperation.add(changeRequest, user, "alice", admin));

            service.complete(OPERATION_ID, gid(null));

            assertThat(user.getUserGroups()).extracting(ug -> ug.getGroup().getGroupName()).containsExactly("teamx");
            assertThat(changeRequest.getStatus()).isEqualTo(Status.FULFILLED);
            assertThat(operation.getStatus()).isEqualTo(GroupOperationStatus.APPLIED);
            verify(alarmService).sendGroupAddedEmail(changeRequest, "승인합니다", List.of("teamx"));
            // 승인을 누른 때가 아니라 반영이 끝난 지금, 접수 알림에 결과를 댓글로 남긴다.
            verify(alarmService).sendChangeRequestDecidedNotification(argThat(decision -> decision.approved()
                    && decision.changeType() == ChangeType.GROUP && "승인합니다".equals(decision.adminComment())));
        }

        @Test
        @DisplayName("작업이 도는 사이 계정이 바뀌었으면 그룹을 기록하지 않고 변경 요청을 승인 대기로 되돌린다")
        void accountChangedDuringTheJobIsNotRecorded() {
            changeRequest.startProcessing(admin, "승인합니다");
            GroupOperation operation = processing(GroupOperation.add(changeRequest, user, "alice", admin));
            ReflectionTestUtils.setField(user, "ubuntuUsername", null);

            service.complete(OPERATION_ID, gid(null));

            assertThat(user.getUserGroups()).isEmpty();
            assertThat(changeRequest.getStatus()).isEqualTo(Status.PENDING);
            assertThat(operation.getErrorCode()).isEqualTo(GroupOperationService.ERROR_ACCOUNT_CHANGED);
            verify(alarmService, never()).sendGroupAddedEmail(any(), any(), anyList());
            verify(alarmService, never()).sendChangeRequestDecidedNotification(any());
        }

        @Test
        @DisplayName("작업이 실패하면 변경 요청을 승인 대기로 되돌리고 관리자에게 알린다")
        void failureReturnsTheChangeRequestToPending() {
            changeRequest.startProcessing(admin, "승인합니다");
            GroupOperation operation = processing(GroupOperation.add(changeRequest, user, "alice", admin));

            service.fail(OPERATION_ID, "AD_GROUP_MEMBER_FAILED");

            assertThat(changeRequest.getStatus()).isEqualTo(Status.PENDING);
            assertThat(changeRequest.getReviewedBy()).isNull();
            assertThat(operation.getStatus()).isEqualTo(GroupOperationStatus.FAILED);
            assertThat(user.getUserGroups()).isEmpty();
            assertThat(Alerts.needsAction(alarmService)).hasSize(1);
        }
    }

    @Nested
    @DisplayName("그룹 제거")
    class Remove {

        @BeforeEach
        void joined() {
            user.addGroupIfAbsent(teamx);
        }

        @Test
        @DisplayName("작업으로 등록만 하고 DB 의 소속은 작업이 성공할 때까지 그대로 둔다")
        void registersJobAndLeavesDbUntouched() {
            GroupOperationResponseDTO response = service.requestRemove(USER_ID, 3L, ADMIN_ID);

            assertThat(response.status()).isEqualTo("PROCESSING");
            assertThat(registered()).isEqualTo(GroupChangeRegisterRequestDTO.remove(OPERATION_ID, "alice", "teamx"));
            assertThat(user.getUserGroups()).hasSize(1);
        }

        @Test
        @DisplayName("리눅스 계정명이 없으면 작업 없이 DB 만 정리한다")
        void noUbuntuUsernameSkipsTheJob() {
            ReflectionTestUtils.setField(user, "ubuntuUsername", null);

            GroupOperationResponseDTO response = service.requestRemove(USER_ID, 3L, ADMIN_ID);

            assertThat(response.status()).isEqualTo("APPLIED");
            assertThat(response.operationId()).isNull();
            assertThat(user.getUserGroups()).isEmpty();
            verifyNoInteractions(jobClient);
        }

        @Test
        @DisplayName("없는 그룹은 404, 같은 제거가 반영 중이면 409 — 둘 다 등록하지 않는다")
        void unknownGroupOrDuplicateIsNotRegistered() {
            when(groupRepository.findById(9L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.requestRemove(USER_ID, 9L, ADMIN_ID))
                    .extracting("errorCode").isEqualTo(ErrorCode.GROUP_NOT_FOUND);

            when(operationRepository.existsByKindAndUser_UserIdAndGroup_GroupIdAndStatus(
                    GroupOperationKind.REMOVE, USER_ID, 3L, GroupOperationStatus.PROCESSING)).thenReturn(true);
            assertThatThrownBy(() -> service.requestRemove(USER_ID, 3L, ADMIN_ID))
                    .extracting("errorCode").isEqualTo(ErrorCode.GROUP_OPERATION_IN_PROGRESS);

            verifyNoInteractions(jobClient);
        }

        @Test
        @DisplayName("작업이 성공하면 계정과 살아 있는 신청에서 그룹을 뺀다")
        void successRemovesFromAccountAndActiveRequests() {
            Request active = mock(Request.class);
            when(requestRepository.findAllByUser_UserIdAndStatusIn(USER_ID, Status.activeStatuses()))
                    .thenReturn(List.of(active));
            GroupOperation operation = processing(GroupOperation.remove(user, teamx, admin));

            service.complete(OPERATION_ID, gid(null));

            assertThat(user.getUserGroups()).isEmpty();
            verify(active).removeGroup(3L);
            assertThat(operation.getStatus()).isEqualTo(GroupOperationStatus.APPLIED);
        }

        @Test
        @DisplayName("작업이 실패하면 DB 의 소속은 그대로다")
        void failureLeavesMembership() {
            GroupOperation operation = processing(GroupOperation.remove(user, teamx, admin));

            service.fail(OPERATION_ID, "AD_GROUP_MEMBER_FAILED");

            assertThat(user.getUserGroups()).hasSize(1);
            assertThat(operation.getStatus()).isEqualTo(GroupOperationStatus.FAILED);
        }
    }

    @Test
    @DisplayName("이미 끝난 작업은 결과가 다시 와도 건드리지 않는다")
    void finishedOperationIsNotAppliedTwice() {
        GroupOperation operation = processing(GroupOperation.create(user, "teamy", null));
        service.complete(OPERATION_ID, gid(70001L));

        service.complete(OPERATION_ID, gid(70002L));
        service.fail(OPERATION_ID, "LATE");

        verify(groupRepository).save(any());
        assertThat(operation.getStatus()).isEqualTo(GroupOperationStatus.APPLIED);
    }

    @Test
    @DisplayName("진행 상태는 요청한 사람·대상 계정의 주인·관리자만 본다. 끝난 생성은 만들어진 그룹을 함께 준다")
    void statusIsVisibleOnlyToThoseInvolved() {
        GroupOperation operation = processing(GroupOperation.create(user, "teamy", null));
        when(groupRepository.findByGroupName("teamy")).thenReturn(Optional.of(group(4L, "teamy", 70001L)));

        assertThat(service.get(OPERATION_ID, USER_ID, false).group()).isNull();
        assertThatThrownBy(() -> service.get(OPERATION_ID, 99L, false))
                .extracting("errorCode").isEqualTo(ErrorCode.GROUP_OPERATION_NOT_FOUND);

        operation.markApplied();
        GroupOperationResponseDTO applied = service.get(OPERATION_ID, 99L, true);
        assertThat(applied.status()).isEqualTo("APPLIED");
        assertThat(applied.group().ubuntuGid()).isEqualTo(70001L);
    }
}
