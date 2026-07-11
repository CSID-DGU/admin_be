package DGU_AI_LAB.admin_be.domain.groups.service;

import DGU_AI_LAB.admin_be.domain.groups.dto.request.CreateGroupRequestDTO;
import DGU_AI_LAB.admin_be.domain.groups.dto.response.GroupResponseDTO;
import DGU_AI_LAB.admin_be.domain.groups.entity.Group;
import DGU_AI_LAB.admin_be.domain.groups.repository.GroupRepository;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.List;
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
    private RequestRepository requestRepository;

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

        @SuppressWarnings("unchecked")
        private WebClient.ResponseSpec mockWebClientSuccess(
                GroupService.ConfigServerGroupResponse response) {
            WebClient.RequestBodyUriSpec uriSpec = mock(WebClient.RequestBodyUriSpec.class);
            WebClient.RequestBodySpec bodySpec = mock(WebClient.RequestBodySpec.class);
            WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);
            doReturn(uriSpec).when(groupCreationWebClient).put();
            doReturn(bodySpec).when(uriSpec).uri(anyString());
            doReturn(bodySpec).when(bodySpec).bodyValue(any());
            doReturn(responseSpec).when(bodySpec).retrieve();
            doReturn(responseSpec).when(responseSpec).onStatus(any(), any());
            doReturn(Mono.just(response))
                    .when(responseSpec).bodyToMono(GroupService.ConfigServerGroupResponse.class);
            return responseSpec;
        }

        @Test
        @DisplayName("유효한 요청으로 그룹을 생성하면 GroupResponseDTO를 반환한다")
        void createGroup_success() {
            when(groupRepository.existsByGroupName("developers")).thenReturn(false);
            when(groupRepository.existsByUbuntuGid(2000L)).thenReturn(false);

            mockWebClientSuccess(new GroupService.ConfigServerGroupResponse(
                    new GroupService.ConfigServerGroupInfo("developers", 2000L)));

            Group savedGroup = Group.builder().groupName("developers").ubuntuGid(2000L).build();
            when(groupRepository.save(any(Group.class))).thenReturn(savedGroup);

            CreateGroupRequestDTO dto = new CreateGroupRequestDTO("developers", null);

            GroupResponseDTO result = groupService.createGroup(dto, 1L);

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

        @Test
        @DisplayName("infra가 유효하지 않은 GID(0)를 반환하면 BusinessException을 던진다")
        void createGroup_throwsException_whenInfraReturnsInvalidGid() {
            when(groupRepository.existsByGroupName("developers")).thenReturn(false);

            mockWebClientSuccess(new GroupService.ConfigServerGroupResponse(
                    new GroupService.ConfigServerGroupInfo("developers", 0L)));

            CreateGroupRequestDTO dto = new CreateGroupRequestDTO("developers", null);

            assertThatThrownBy(() -> groupService.createGroup(dto, 1L))
                    .isInstanceOf(BusinessException.class);
            verify(groupRepository, never()).save(any(Group.class));
        }

        @Test
        @DisplayName("infra가 null GID를 반환하면 BusinessException을 던진다")
        void createGroup_throwsException_whenInfraReturnsNullGid() {
            when(groupRepository.existsByGroupName("developers")).thenReturn(false);

            mockWebClientSuccess(new GroupService.ConfigServerGroupResponse(null));

            CreateGroupRequestDTO dto = new CreateGroupRequestDTO("developers", null);

            assertThatThrownBy(() -> groupService.createGroup(dto, 1L))
                    .isInstanceOf(BusinessException.class);
            verify(groupRepository, never()).save(any(Group.class));
        }

        @Test
        @DisplayName("onStatus 핸들러에서 4xx 에러 발생 시 BusinessException을 던진다")
        @SuppressWarnings("unchecked")
        void createGroup_throwsException_when4xxFromOnStatus() {
            when(groupRepository.existsByGroupName("developers")).thenReturn(false);

            WebClient.RequestBodyUriSpec uriSpec = mock(WebClient.RequestBodyUriSpec.class);
            WebClient.RequestBodySpec bodySpec = mock(WebClient.RequestBodySpec.class);
            WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);
            doReturn(uriSpec).when(groupCreationWebClient).put();
            doReturn(bodySpec).when(uriSpec).uri(anyString());
            doReturn(bodySpec).when(bodySpec).bodyValue(any());
            doReturn(responseSpec).when(bodySpec).retrieve();
            doReturn(responseSpec).when(responseSpec).onStatus(any(), any());
            doReturn(Mono.error(new BusinessException(ErrorCode.DUPLICATE_GROUP_NAME)))
                    .when(responseSpec).bodyToMono(GroupService.ConfigServerGroupResponse.class);

            CreateGroupRequestDTO dto = new CreateGroupRequestDTO("developers", null);

            assertThatThrownBy(() -> groupService.createGroup(dto, 1L))
                    .isInstanceOf(BusinessException.class);
            verify(groupRepository, never()).save(any(Group.class));
        }

        @Test
        @DisplayName("WebClientResponseException 발생 시 GROUP_CREATION_FAILED BusinessException을 던진다")
        @SuppressWarnings("unchecked")
        void createGroup_throwsException_whenWebClientThrowsResponseException() {
            when(groupRepository.existsByGroupName("developers")).thenReturn(false);

            WebClient.RequestBodyUriSpec uriSpec = mock(WebClient.RequestBodyUriSpec.class);
            WebClient.RequestBodySpec bodySpec = mock(WebClient.RequestBodySpec.class);
            WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);
            doReturn(uriSpec).when(groupCreationWebClient).put();
            doReturn(bodySpec).when(uriSpec).uri(anyString());
            doReturn(bodySpec).when(bodySpec).bodyValue(any());
            doReturn(responseSpec).when(bodySpec).retrieve();
            doReturn(responseSpec).when(responseSpec).onStatus(any(), any());
            doReturn(Mono.error(WebClientResponseException.create(
                    HttpStatus.INTERNAL_SERVER_ERROR.value(), "Server Error", null, null, null)))
                    .when(responseSpec).bodyToMono(GroupService.ConfigServerGroupResponse.class);

            CreateGroupRequestDTO dto = new CreateGroupRequestDTO("developers", null);

            assertThatThrownBy(() -> groupService.createGroup(dto, 1L))
                    .isInstanceOf(BusinessException.class);
            verify(groupRepository, never()).save(any(Group.class));
        }

        @Test
        @DisplayName("응답 파싱 오류(예: 역직렬화 실패) 시 GROUP_CREATION_FAILED BusinessException을 던진다")
        @SuppressWarnings("unchecked")
        void createGroup_throwsException_whenDeserializationFails() {
            when(groupRepository.existsByGroupName("developers")).thenReturn(false);

            WebClient.RequestBodyUriSpec uriSpec = mock(WebClient.RequestBodyUriSpec.class);
            WebClient.RequestBodySpec bodySpec = mock(WebClient.RequestBodySpec.class);
            WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);
            doReturn(uriSpec).when(groupCreationWebClient).put();
            doReturn(bodySpec).when(uriSpec).uri(anyString());
            doReturn(bodySpec).when(bodySpec).bodyValue(any());
            doReturn(responseSpec).when(bodySpec).retrieve();
            doReturn(responseSpec).when(responseSpec).onStatus(any(), any());
            doReturn(Mono.error(new RuntimeException("JSON parse error")))
                    .when(responseSpec).bodyToMono(GroupService.ConfigServerGroupResponse.class);

            CreateGroupRequestDTO dto = new CreateGroupRequestDTO("developers", null);

            assertThatThrownBy(() -> groupService.createGroup(dto, 1L))
                    .isInstanceOf(BusinessException.class);
            verify(groupRepository, never()).save(any(Group.class));
        }

        @Test
        @DisplayName("이미 DB에 존재하는 GID를 외부 API가 반환하면 BusinessException을 던진다")
        void createGroup_throwsException_whenGidAlreadyExistsInDb() {
            when(groupRepository.existsByGroupName("developers")).thenReturn(false);
            when(groupRepository.existsByUbuntuGid(2000L)).thenReturn(true);

            mockWebClientSuccess(new GroupService.ConfigServerGroupResponse(
                    new GroupService.ConfigServerGroupInfo("developers", 2000L)));

            CreateGroupRequestDTO dto = new CreateGroupRequestDTO("developers", null);

            assertThatThrownBy(() -> groupService.createGroup(dto, 1L))
                    .isInstanceOf(BusinessException.class);
            verify(groupRepository, never()).save(any(Group.class));
        }
    }
}
