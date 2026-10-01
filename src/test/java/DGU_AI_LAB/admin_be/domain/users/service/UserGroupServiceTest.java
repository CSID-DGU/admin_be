package DGU_AI_LAB.admin_be.domain.users.service;

import DGU_AI_LAB.admin_be.domain.groups.dto.response.GroupOperationResponseDTO;
import DGU_AI_LAB.admin_be.domain.groups.dto.response.GroupResponseDTO;
import DGU_AI_LAB.admin_be.domain.groups.entity.Group;
import DGU_AI_LAB.admin_be.domain.groups.service.GroupOperationService;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserGroupServiceTest {

    @InjectMocks
    private UserGroupService userGroupService;

    @Mock private UserRepository userRepository;
    @Mock private GroupOperationService groupOperationService;

    private static Group group(Long id, String name, Long gid) {
        Group g = Group.builder().groupName(name).ubuntuGid(gid).build();
        ReflectionTestUtils.setField(g, "groupId", id);
        return g;
    }

    @Test
    @DisplayName("그룹 목록은 이름순이다")
    void groupsAreSortedByName() {
        User user = User.builder().email("a@dgu.ac.kr").name("앨리스").ubuntuUsername("alice").build();
        user.addGroupIfAbsent(group(4L, "teamy", 70001L));
        user.addGroupIfAbsent(group(3L, "teamx", 70000L));
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));

        assertThat(userGroupService.getGroupsOfUser(5L))
                .extracting(GroupResponseDTO::groupName)
                .containsExactly("teamx", "teamy");
    }

    @Test
    @DisplayName("그룹 제거는 작업 등록으로 넘기고 그 진행 상태를 돌려준다")
    void removalIsRegisteredAsAJob() {
        GroupOperationResponseDTO processing =
                new GroupOperationResponseDTO(7L, "REMOVE", "PROCESSING", "teamx", null, null);
        when(groupOperationService.requestRemove(5L, 3L, 1L)).thenReturn(processing);

        assertThat(userGroupService.removeUserFromGroup(5L, 3L, 1L)).isEqualTo(processing);
    }
}
