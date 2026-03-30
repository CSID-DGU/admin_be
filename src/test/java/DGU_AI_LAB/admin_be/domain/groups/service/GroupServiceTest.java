package DGU_AI_LAB.admin_be.domain.groups.service;

import DGU_AI_LAB.admin_be.domain.groups.dto.request.CreateGroupRequestDTO;
import DGU_AI_LAB.admin_be.domain.groups.dto.response.GroupResponseDTO;
import DGU_AI_LAB.admin_be.domain.groups.entity.Group;
import DGU_AI_LAB.admin_be.domain.groups.repository.GroupRepository;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.usedIds.entity.UsedId;
import DGU_AI_LAB.admin_be.domain.usedIds.repository.UsedIdRepository;
import DGU_AI_LAB.admin_be.domain.usedIds.service.IdAllocationService;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GroupServiceTest {

    @InjectMocks
    private GroupService groupService;

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private UsedIdRepository usedIdRepository;

    @Mock
    private RequestRepository requestRepository;

    @Mock
    private IdAllocationService idAllocationService;

    @Mock
    private WebClient groupCreationWebClient;

    @Nested
    @DisplayName("getAllGroups")
    class GetAllGroups {

        @Test
        @DisplayName("그룹이 있으면 GroupResponseDTO 리스트를 반환한다")
        void getAllGroups_returnsList() {
            Group group1 = Group.builder().groupName("developers").ubuntuGid(2000L).build();
            Group group2 = Group.builder().groupName("admins").ubuntuGid(2001L).build();
            when(groupRepository.findAll()).thenReturn(List.of(group1, group2));

            List<GroupResponseDTO> result = groupService.getAllGroups();

            assertThat(result).hasSize(2);
            assertThat(result).extracting("groupName").containsExactlyInAnyOrder("developers", "admins");
        }

        @Test
        @DisplayName("그룹이 없으면 BusinessException을 던진다")
        void getAllGroups_throwsException_whenEmpty() {
            when(groupRepository.findAll()).thenReturn(List.of());

            assertThatThrownBy(() -> groupService.getAllGroups())
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    @DisplayName("createGroup")
    class CreateGroup {

        @Test
        @DisplayName("유효한 요청으로 그룹을 생성하면 GroupResponseDTO를 반환한다")
        @SuppressWarnings("unchecked")
        void createGroup_success() {
            // Arrange
            when(groupRepository.existsByGroupName("developers")).thenReturn(false);
            when(idAllocationService.allocateNewGid()).thenReturn(2000L);

            // Mock WebClient chain
            WebClient.RequestBodyUriSpec uriSpec = mock(WebClient.RequestBodyUriSpec.class);
            WebClient.RequestBodySpec bodySpec = mock(WebClient.RequestBodySpec.class);
            WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);
            doReturn(uriSpec).when(groupCreationWebClient).put();
            doReturn(bodySpec).when(uriSpec).uri(anyString());
            doReturn(bodySpec).when(bodySpec).bodyValue(any());
            doReturn(responseSpec).when(bodySpec).retrieve();
            doReturn(Mono.just(Map.of("result", "ok"))).when(responseSpec).bodyToMono(Map.class);

            UsedId usedId = UsedId.builder().idValue(2000L).build();
            when(usedIdRepository.findById(2000L)).thenReturn(Optional.of(usedId));

            Group savedGroup = Group.builder().groupName("developers").ubuntuGid(2000L).build();
            when(groupRepository.save(any(Group.class))).thenReturn(savedGroup);

            CreateGroupRequestDTO dto = new CreateGroupRequestDTO("developers", null);

            // Act
            GroupResponseDTO result = groupService.createGroup(dto, 1L);

            // Assert
            assertThat(result.groupName()).isEqualTo("developers");
            assertThat(result.ubuntuGid()).isEqualTo(2000L);
        }

        @Test
        @DisplayName("중복된 그룹명으로 생성하면 BusinessException을 던진다")
        void createGroup_throwsException_whenGroupNameDuplicated() {
            when(groupRepository.existsByGroupName("developers")).thenReturn(true);

            CreateGroupRequestDTO dto = new CreateGroupRequestDTO("developers", null);

            assertThatThrownBy(() -> groupService.createGroup(dto, 1L))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("ubuntuUsername이 제공됐지만 해당 유저의 요청이 아니면 BusinessException을 던진다")
        void createGroup_throwsException_whenUsernameNotOwnedByUser() {
            when(requestRepository.existsByUbuntuUsernameAndUser_UserId("otheruser", 1L)).thenReturn(false);

            CreateGroupRequestDTO dto = new CreateGroupRequestDTO("newgroup", "otheruser");

            assertThatThrownBy(() -> groupService.createGroup(dto, 1L))
                    .isInstanceOf(BusinessException.class);
        }
    }
}
