package DGU_AI_LAB.admin_be.domain.users.service;

import DGU_AI_LAB.admin_be.domain.groups.dto.response.GroupResponseDTO;
import DGU_AI_LAB.admin_be.domain.groups.entity.Group;
import DGU_AI_LAB.admin_be.domain.groups.repository.GroupRepository;
import DGU_AI_LAB.admin_be.domain.groups.service.GroupService;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserGroupServiceTest {

    @InjectMocks
    private UserGroupService userGroupService;

    @Mock private UserRepository userRepository;
    @Mock private GroupRepository groupRepository;
    @Mock private RequestRepository requestRepository;
    @Mock private GroupService groupService;
    @Mock private PlatformTransactionManager transactionManager;
    @Mock private TransactionStatus transactionStatus;

    private User user;
    private Group teamx;
    private Group teamy;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        user = User.builder().email("a@dgu.ac.kr").name("앨리스").ubuntuUsername("alice").build();
        ReflectionTestUtils.setField(user, "userId", 5L);
        teamx = group(3L, "teamx", 70000L);
        teamy = group(4L, "teamy", 70001L);
        user.addGroupIfAbsent(teamx);
        user.addGroupIfAbsent(teamy);
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(groupRepository.findById(3L)).thenReturn(Optional.of(teamx));
    }

    private static Group group(Long id, String name, Long gid) {
        Group g = Group.builder().groupName(name).ubuntuGid(gid).build();
        ReflectionTestUtils.setField(g, "groupId", id);
        return g;
    }

    @Test
    @DisplayName("그룹 목록은 이름순이다")
    void groupsAreSortedByName() {
        assertThat(userGroupService.getGroupsOfUser(5L))
                .extracting(GroupResponseDTO::groupName)
                .containsExactly("teamx", "teamy");
    }

    @Test
    @DisplayName("AD에서 뺀 뒤 NAS 캐시를 비우고, 그다음에 계정과 살아 있는 신청에서 그룹을 뺀다")
    void removesFromAdThenDb() {
        Request active = mock(Request.class);
        when(requestRepository.findAllByUser_UserIdAndStatusIn(5L, Status.activeStatuses()))
                .thenReturn(List.of(active));

        userGroupService.removeUserFromGroup(5L, 3L);

        InOrder order = inOrder(groupService, requestRepository);
        order.verify(groupService).removeUserFromGroup("alice", "teamx");
        order.verify(groupService).triggerNasGssFlush("alice");
        order.verify(requestRepository).findAllByUser_UserIdAndStatusIn(5L, Status.activeStatuses());
        verify(active).removeGroup(3L);
        assertThat(user.getUserGroups()).extracting(ug -> ug.getGroup().getGroupName()).containsExactly("teamy");
    }

    @Test
    @DisplayName("AD 반영이 실패하면 DB는 그대로 둔다")
    void adFailureLeavesDbUntouched() {
        doThrow(new BusinessException(ErrorCode.AD_GROUP_REMOVE_FAILED))
                .when(groupService).removeUserFromGroup("alice", "teamx");

        assertThatThrownBy(() -> userGroupService.removeUserFromGroup(5L, 3L))
                .isInstanceOf(BusinessException.class);

        verify(groupService, never()).triggerNasGssFlush(anyString());
        verify(requestRepository, never()).findAllByUser_UserIdAndStatusIn(any(), any());
        assertThat(user.getUserGroups()).hasSize(2);
    }

    @Test
    @DisplayName("우분투 계정이 없으면 인프라는 부르지 않고 DB만 정리한다")
    void noUbuntuAccountSkipsInfra() {
        ReflectionTestUtils.setField(user, "ubuntuUsername", null);

        userGroupService.removeUserFromGroup(5L, 3L);

        verify(groupService, never()).removeUserFromGroup(anyString(), anyString());
        assertThat(user.getUserGroups()).hasSize(1);
    }

    @Test
    @DisplayName("없는 그룹이면 인프라를 부르기 전에 404")
    void unknownGroupIsNotFound() {
        when(groupRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userGroupService.removeUserFromGroup(5L, 9L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.GROUP_NOT_FOUND);
        verify(groupService, never()).removeUserFromGroup(anyString(), anyString());
    }
}
