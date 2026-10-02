package DGU_AI_LAB.admin_be.domain.groups.service;

import DGU_AI_LAB.admin_be.domain.groups.dto.request.CreateGroupRequestDTO;
import DGU_AI_LAB.admin_be.domain.groups.dto.response.GroupResponseDTO;
import DGU_AI_LAB.admin_be.domain.groups.entity.Group;
import DGU_AI_LAB.admin_be.domain.groups.entity.GroupOperationKind;
import DGU_AI_LAB.admin_be.domain.groups.entity.GroupOperationStatus;
import DGU_AI_LAB.admin_be.domain.groups.repository.GroupOperationRepository;
import DGU_AI_LAB.admin_be.domain.groups.repository.GroupRepository;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import DGU_AI_LAB.admin_be.global.validation.ReservedLinuxNames;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupServiceTest {

    private static final Long USER_ID = 5L;

    @InjectMocks private GroupService groupService;
    @Mock private GroupRepository groupRepository;
    @Mock private GroupOperationRepository operationRepository;
    @Mock private UserRepository userRepository;
    @Mock private ReservedLinuxNames reservedLinuxNames;
    @Mock private GroupCreateThrottle groupCreateThrottle;

    @Test
    @DisplayName("그룹이 있으면 GroupResponseDTO 리스트를 반환한다")
    void getAllGroups_returnsList() {
        Group group1 = Group.builder().groupName("developers").ubuntuGid(2000L).build();
        Group group2 = Group.builder().groupName("admins").ubuntuGid(2001L).build();
        when(groupRepository.findAll()).thenReturn(List.of(group1, group2));

        List<GroupResponseDTO> result = groupService.getAllGroups();

        assertThat(result).extracting("groupName").containsExactlyInAnyOrder("developers", "admins");
    }

    @Test
    @DisplayName("그룹이 없으면 빈 리스트를 반환한다")
    void getAllGroups_returnsEmptyList_whenNoGroups() {
        when(groupRepository.findAll()).thenReturn(List.of());

        assertThat(groupService.getAllGroups()).isEmpty();
    }

    @Test
    @DisplayName("승인 대기 그룹도 목록에 gid 없이 나온다 — 다른 사용자가 신청서에서 고를 수 있다")
    void getAllGroups_includesPendingGroups() {
        when(groupRepository.findAll()).thenReturn(List.of(Group.builder().groupName("vision-lab").build()));

        assertThat(groupService.getAllGroups()).singleElement()
                .satisfies(g -> assertThat(g.ubuntuGid()).isNull());
    }

    @Test
    @DisplayName("그룹을 gid 없이(승인 대기) DB 에만 만들고 바로 돌려준다")
    void createsPendingGroupInDbOnly() {
        when(groupRepository.saveAndFlush(any(Group.class))).thenAnswer(invocation -> {
            Group saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "groupId", 11L);
            return saved;
        });

        GroupResponseDTO response = groupService.createGroup(new CreateGroupRequestDTO("vision-lab"), USER_ID);

        assertThat(response.groupId()).isEqualTo(11L);
        assertThat(response.groupName()).isEqualTo("vision-lab");
        assertThat(response.ubuntuGid()).isNull();
        ArgumentCaptor<Group> saved = ArgumentCaptor.forClass(Group.class);
        verify(groupRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().isPending()).isTrue();
        verify(groupCreateThrottle).acquire(USER_ID);
    }

    @Test
    @DisplayName("이미 있는 이름·계정명과 같은 이름·예약 이름·예전 방식으로 만드는 중인 이름은 만들지 않고 한도도 쓰지 않는다")
    void rejectedNamesAreNotCreated() {
        when(groupRepository.existsByGroupName("taken")).thenReturn(true);
        when(userRepository.existsByUbuntuUsername("bob")).thenReturn(true);
        when(reservedLinuxNames.contains("video")).thenReturn(true);
        when(operationRepository.existsByKindAndGroupNameAndStatus(
                GroupOperationKind.CREATE, "making", GroupOperationStatus.PROCESSING)).thenReturn(true);

        assertRejected("taken", ErrorCode.DUPLICATE_GROUP_NAME);
        assertRejected("bob", ErrorCode.GROUP_NAME_CONFLICTS_USER);
        assertRejected("video", ErrorCode.RESERVED_GROUP_NAME);
        assertRejected("making", ErrorCode.GROUP_OPERATION_IN_PROGRESS);

        verifyNoInteractions(groupCreateThrottle);
        verify(groupRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("하루 한도를 넘으면 만들지 않는다")
    void throttledRequestIsNotCreated() {
        doThrow(new BusinessException(ErrorCode.TOO_MANY_GROUP_CREATIONS)).when(groupCreateThrottle).acquire(USER_ID);

        assertRejected("vision-lab", ErrorCode.TOO_MANY_GROUP_CREATIONS);
        verify(groupRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("같은 이름을 동시에 만들어 unique 제약에 걸리면 이름 중복으로 돌려준다")
    void concurrentDuplicateBecomesDuplicateName() {
        when(groupRepository.saveAndFlush(any(Group.class))).thenThrow(new DataIntegrityViolationException("dup"));

        assertRejected("vision-lab", ErrorCode.DUPLICATE_GROUP_NAME);
    }

    private void assertRejected(String groupName, ErrorCode expected) {
        assertThatThrownBy(() -> groupService.createGroup(new CreateGroupRequestDTO(groupName), USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(expected);
    }
}
