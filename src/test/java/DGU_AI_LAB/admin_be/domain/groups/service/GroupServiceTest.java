package DGU_AI_LAB.admin_be.domain.groups.service;

import DGU_AI_LAB.admin_be.domain.groups.dto.response.GroupResponseDTO;
import DGU_AI_LAB.admin_be.domain.groups.entity.Group;
import DGU_AI_LAB.admin_be.domain.groups.repository.GroupRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupServiceTest {

    @InjectMocks
    private GroupService groupService;

    @Mock
    private GroupRepository groupRepository;

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
}
