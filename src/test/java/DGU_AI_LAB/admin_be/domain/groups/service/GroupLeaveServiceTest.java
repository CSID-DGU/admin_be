package DGU_AI_LAB.admin_be.domain.groups.service;

import DGU_AI_LAB.admin_be.domain.groups.entity.Group;
import DGU_AI_LAB.admin_be.domain.groups.entity.GroupLeaveRequest;
import DGU_AI_LAB.admin_be.domain.groups.entity.GroupLeaveStatus;
import DGU_AI_LAB.admin_be.domain.groups.repository.GroupLeaveRequestRepository;
import DGU_AI_LAB.admin_be.domain.groups.repository.GroupRepository;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("GroupLeaveService")
class GroupLeaveServiceTest {

    @Mock private GroupLeaveRequestRepository leaveRepository;
    @Mock private GroupRepository groupRepository;
    @Mock private UserRepository userRepository;
    @Mock private GroupOperationService groupOperationService;

    private GroupLeaveService service;
    private User user;
    private Group group;

    @BeforeEach
    void setUp() {
        service = new GroupLeaveService(leaveRepository, groupRepository, userRepository, groupOperationService);
        user = User.builder().email("a@dgu.ac.kr").password("pw").name("홍").studentId("1")
                .phone("010").department("과").build();
        ReflectionTestUtils.setField(user, "userId", 7L);
        group = Group.builder().groupName("team").build();
        ReflectionTestUtils.setField(group, "groupId", 3L);
        user.addGroupIfAbsent(group);
        when(userRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(user));
        when(groupRepository.findById(3L)).thenReturn(Optional.of(group));
        when(leaveRepository.save(any(GroupLeaveRequest.class))).thenAnswer(i -> i.getArgument(0));
    }

    private static ErrorCode codeOf(Throwable e) {
        return ((BusinessException) e).getErrorCode();
    }

    @Test
    @DisplayName("속한 그룹의 탈퇴를 신청하면 승인 대기로 저장한다")
    void requestsLeave() {
        GroupLeaveRequest saved = service.request(7L, 3L);

        assertThat(saved.getStatus()).isEqualTo(GroupLeaveStatus.PENDING);
        assertThat(saved.getUser()).isSameAs(user);
        assertThat(saved.getGroup()).isSameAs(group);
    }

    @Test
    @DisplayName("속하지 않은 그룹이면 GROUP_LEAVE_NOT_MEMBER")
    void rejectsNonMember() {
        user.removeGroup(3L);

        assertThatThrownBy(() -> service.request(7L, 3L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.GROUP_LEAVE_NOT_MEMBER));
        verify(leaveRepository, never()).save(any());
    }

    @Test
    @DisplayName("기본 그룹(계정 이름과 같은 그룹)은 접수에서 PRIMARY_GROUP_REMOVAL로 거절한다")
    void rejectsPrimaryGroup() {
        ReflectionTestUtils.setField(user, "ubuntuUsername", "team");

        assertThatThrownBy(() -> service.request(7L, 3L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.PRIMARY_GROUP_REMOVAL));
        verify(leaveRepository, never()).save(any());
    }

    @Test
    @DisplayName("제거 작업 등록이 실패하면 신청은 대기로 남는다")
    void approveFailureKeepsPending() {
        GroupLeaveRequest pending = GroupLeaveRequest.pending(user, group);
        when(leaveRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(pending));
        when(groupOperationService.requestRemove(7L, 3L, 9L)).thenThrow(new BusinessException(ErrorCode.GROUP_CHANGE_FAILED));

        assertThatThrownBy(() -> service.approve(5L, 9L)).isInstanceOf(BusinessException.class);

        assertThat(pending.getStatus()).isEqualTo(GroupLeaveStatus.PENDING);
    }

    @Test
    @DisplayName("같은 그룹의 대기 중인 신청이 있으면 GROUP_LEAVE_ALREADY_REQUESTED")
    void rejectsDuplicatePending() {
        when(leaveRepository.existsByUser_UserIdAndGroup_GroupIdAndStatus(7L, 3L, GroupLeaveStatus.PENDING))
                .thenReturn(true);

        assertThatThrownBy(() -> service.request(7L, 3L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.GROUP_LEAVE_ALREADY_REQUESTED));
    }

    @Test
    @DisplayName("승인하면 기존 그룹 제거 작업을 관리자 이름으로 등록하고 승인됨으로 바꾼다")
    void approveRegistersRemoval() {
        GroupLeaveRequest pending = GroupLeaveRequest.pending(user, group);
        when(leaveRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(pending));
        User admin = User.builder().email("admin@dgu.ac.kr").password("pw").name("관").studentId("2")
                .phone("010").department("과").build();
        when(userRepository.getReferenceById(9L)).thenReturn(admin);

        service.approve(5L, 9L);

        assertThat(pending.getStatus()).isEqualTo(GroupLeaveStatus.APPROVED);
        assertThat(pending.getDecidedBy()).isSameAs(admin);
        verify(groupOperationService).requestRemove(7L, 3L, 9L);
    }

    @Test
    @DisplayName("승인은 신청 행을 한 번만 잠가 확인·등록·승인 기록을 한 트랜잭션 안에서 한다")
    void approveLocksRequestOnce() {
        GroupLeaveRequest pending = GroupLeaveRequest.pending(user, group);
        when(leaveRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(pending));

        service.approve(5L, 9L);

        verify(leaveRepository, times(1)).findByIdForUpdate(5L);
        verify(groupOperationService).requestRemove(7L, 3L, 9L);
    }

    @Test
    @DisplayName("이미 처리된 신청은 승인할 수 없다")
    void approveRejectsAlreadyDecided() {
        GroupLeaveRequest done = GroupLeaveRequest.pending(user, group);
        ReflectionTestUtils.setField(done, "status", GroupLeaveStatus.REJECTED);
        when(leaveRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(done));

        assertThatThrownBy(() -> service.approve(5L, 9L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.INVALID_REQUEST_STATUS));
        verify(groupOperationService, never()).requestRemove(any(), any(), any());
    }

    @Test
    @DisplayName("거절하면 거절됨으로 바꾸고 제거 작업은 등록하지 않는다")
    void rejectDoesNotRemove() {
        GroupLeaveRequest pending = GroupLeaveRequest.pending(user, group);
        when(leaveRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(pending));
        when(userRepository.getReferenceById(9L)).thenReturn(user);

        service.reject(5L, 9L);

        assertThat(pending.getStatus()).isEqualTo(GroupLeaveStatus.REJECTED);
        verify(groupOperationService, never()).requestRemove(any(), any(), any());
    }
}
