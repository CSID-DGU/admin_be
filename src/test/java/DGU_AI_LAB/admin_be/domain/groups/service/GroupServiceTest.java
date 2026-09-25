package DGU_AI_LAB.admin_be.domain.groups.service;

import DGU_AI_LAB.admin_be.domain.alarm.service.AlarmService;
import DGU_AI_LAB.admin_be.domain.groups.dto.request.CreateGroupRequestDTO;
import DGU_AI_LAB.admin_be.domain.groups.dto.response.GroupResponseDTO;
import DGU_AI_LAB.admin_be.domain.groups.entity.Group;
import DGU_AI_LAB.admin_be.domain.groups.repository.GroupRepository;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GroupServiceTest {

    @InjectMocks
    private GroupService groupService;

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private RequestRepository requestRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private WebClient groupCreationWebClient;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private TransactionStatus transactionStatus;

    @Mock
    private AlarmService alarmService;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
    }

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
        @DisplayName("그룹이 없으면 빈 리스트를 반환한다")
        void getAllGroups_returnsEmptyList_whenNoGroups() {
            when(groupRepository.findAll()).thenReturn(List.of());

            List<GroupResponseDTO> result = groupService.getAllGroups();

            assertThat(result).isEmpty();
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
            doReturn(uriSpec).when(groupCreationWebClient).post();
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
            when(groupRepository.saveAndFlush(any(Group.class))).thenReturn(savedGroup);

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
        @DisplayName("이미 등록된 우분투 계정명과 같은 그룹명이면 외부 API를 부르기 전에 GROUP_NAME_CONFLICTS_USER를 던진다")
        void createGroup_throwsException_whenGroupNameEqualsRegisteredUsername() {
            when(groupRepository.existsByGroupName("honggildong")).thenReturn(false);
            when(userRepository.existsByUbuntuUsername("honggildong")).thenReturn(true);

            CreateGroupRequestDTO dto = new CreateGroupRequestDTO("honggildong", null);

            assertThatThrownBy(() -> groupService.createGroup(dto, 1L))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.GROUP_NAME_CONFLICTS_USER);
            verifyNoInteractions(groupCreationWebClient);
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
            verify(groupRepository, never()).saveAndFlush(any(Group.class));
        }

        @Test
        @DisplayName("infra가 null GID를 반환하면 BusinessException을 던진다")
        void createGroup_throwsException_whenInfraReturnsNullGid() {
            when(groupRepository.existsByGroupName("developers")).thenReturn(false);

            mockWebClientSuccess(new GroupService.ConfigServerGroupResponse(null));

            CreateGroupRequestDTO dto = new CreateGroupRequestDTO("developers", null);

            assertThatThrownBy(() -> groupService.createGroup(dto, 1L))
                    .isInstanceOf(BusinessException.class);
            verify(groupRepository, never()).saveAndFlush(any(Group.class));
        }

        @Test
        @DisplayName("onStatus 핸들러에서 4xx 에러 발생 시 BusinessException을 던진다")
        @SuppressWarnings("unchecked")
        void createGroup_throwsException_when4xxFromOnStatus() {
            when(groupRepository.existsByGroupName("developers")).thenReturn(false);

            WebClient.RequestBodyUriSpec uriSpec = mock(WebClient.RequestBodyUriSpec.class);
            WebClient.RequestBodySpec bodySpec = mock(WebClient.RequestBodySpec.class);
            WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);
            doReturn(uriSpec).when(groupCreationWebClient).post();
            doReturn(bodySpec).when(uriSpec).uri(anyString());
            doReturn(bodySpec).when(bodySpec).bodyValue(any());
            doReturn(responseSpec).when(bodySpec).retrieve();
            doReturn(responseSpec).when(responseSpec).onStatus(any(), any());
            doReturn(Mono.error(new BusinessException(ErrorCode.DUPLICATE_GROUP_NAME)))
                    .when(responseSpec).bodyToMono(GroupService.ConfigServerGroupResponse.class);

            CreateGroupRequestDTO dto = new CreateGroupRequestDTO("developers", null);

            assertThatThrownBy(() -> groupService.createGroup(dto, 1L))
                    .isInstanceOf(BusinessException.class);
            verify(groupRepository, never()).saveAndFlush(any(Group.class));
        }

        @Test
        @DisplayName("WebClientResponseException 발생 시 GROUP_CREATION_FAILED BusinessException을 던진다")
        @SuppressWarnings("unchecked")
        void createGroup_throwsException_whenWebClientThrowsResponseException() {
            when(groupRepository.existsByGroupName("developers")).thenReturn(false);

            WebClient.RequestBodyUriSpec uriSpec = mock(WebClient.RequestBodyUriSpec.class);
            WebClient.RequestBodySpec bodySpec = mock(WebClient.RequestBodySpec.class);
            WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);
            doReturn(uriSpec).when(groupCreationWebClient).post();
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
            verify(groupRepository, never()).saveAndFlush(any(Group.class));
        }

        @Test
        @DisplayName("응답 파싱 오류(예: 역직렬화 실패) 시 GROUP_CREATION_FAILED BusinessException을 던진다")
        @SuppressWarnings("unchecked")
        void createGroup_throwsException_whenDeserializationFails() {
            when(groupRepository.existsByGroupName("developers")).thenReturn(false);

            WebClient.RequestBodyUriSpec uriSpec = mock(WebClient.RequestBodyUriSpec.class);
            WebClient.RequestBodySpec bodySpec = mock(WebClient.RequestBodySpec.class);
            WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);
            doReturn(uriSpec).when(groupCreationWebClient).post();
            doReturn(bodySpec).when(uriSpec).uri(anyString());
            doReturn(bodySpec).when(bodySpec).bodyValue(any());
            doReturn(responseSpec).when(bodySpec).retrieve();
            doReturn(responseSpec).when(responseSpec).onStatus(any(), any());
            doReturn(Mono.error(new RuntimeException("JSON parse error")))
                    .when(responseSpec).bodyToMono(GroupService.ConfigServerGroupResponse.class);

            CreateGroupRequestDTO dto = new CreateGroupRequestDTO("developers", null);

            assertThatThrownBy(() -> groupService.createGroup(dto, 1L))
                    .isInstanceOf(BusinessException.class);
            verify(groupRepository, never()).saveAndFlush(any(Group.class));
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
            verify(groupRepository, never()).saveAndFlush(any(Group.class));
        }

        @Test
        @DisplayName("인프라 그룹 생성 후 DB 저장 실패 시 GROUP_CREATION_FAILED를 던진다")
        void createGroup_throwsException_whenDbSaveFails() {
            when(groupRepository.existsByGroupName("developers")).thenReturn(false);
            when(groupRepository.existsByUbuntuGid(2000L)).thenReturn(false);

            mockWebClientSuccess(new GroupService.ConfigServerGroupResponse(
                    new GroupService.ConfigServerGroupInfo("developers", 2000L)));

            when(groupRepository.saveAndFlush(any(Group.class))).thenThrow(new RuntimeException("DB connection lost"));

            CreateGroupRequestDTO dto = new CreateGroupRequestDTO("developers", null);

            assertThatThrownBy(() -> groupService.createGroup(dto, 1L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.GROUP_CREATION_FAILED);

            // 인프라엔 이미 그룹이 만들어졌는데 DB 저장만 실패한 상태라 수동 정리가 필요하다 —
            // 로그만 남기면 아무도 모르므로 Slack 알림이 반드시 나가야 한다.
            verify(alarmService).sendSlackAlert(contains("developers"), isNull());
        }

        @Test
        @DisplayName("사전 검사 통과 후 다른 요청이 같은 그룹명을 먼저 저장하면 DUPLICATE_GROUP_NAME을 던진다")
        void createGroup_throwsDuplicateGroupName_whenUniqueConstraintViolated() {
            when(groupRepository.existsByGroupName("developers")).thenReturn(false);
            when(groupRepository.existsByUbuntuGid(2000L)).thenReturn(false);

            mockWebClientSuccess(new GroupService.ConfigServerGroupResponse(
                    new GroupService.ConfigServerGroupInfo("developers", 2000L)));

            when(groupRepository.saveAndFlush(any(Group.class)))
                    .thenThrow(new DataIntegrityViolationException("duplicate key: group_name"));

            CreateGroupRequestDTO dto = new CreateGroupRequestDTO("developers", null);

            assertThatThrownBy(() -> groupService.createGroup(dto, 1L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.DUPLICATE_GROUP_NAME);

            // 이 경로도 외부 API 호출은 이미 성공한 뒤라 인프라에 고아 그룹이 남는다 — 동일하게 알림 필요.
            verify(alarmService).sendSlackAlert(contains("developers"), isNull());
        }
    }

    @Nested
    @DisplayName("addUserToGroups 오류 매핑")
    class MapAddUserToGroupsError {

        // config-server main.py add_user_groups 가 실제로 내는 본문 그대로.
        private static final String AD_FAILED =
                "{\"detail\":\"failed to add alice to groups in AD\",\"error\":\"AD_GROUP_MEMBER_FAILED\",\"step\":\"ADD_USER_GROUPS\"}";
        private static final String GROUP_MISSING =
                "{\"detail\":\"groups not found: teamx\",\"error\":\"GROUP_NOT_FOUND\",\"step\":\"ADD_USER_GROUPS\"}";
        private static final String USER_MISSING =
                "{\"detail\":\"user not found: alice\",\"error\":\"USER_NOT_FOUND\",\"step\":\"ADD_USER_GROUPS\"}";

        private ErrorCode codeOf(HttpStatus status, String body) {
            return GroupService.mapAddUserToGroupsError(status, body).getErrorCode();
        }

        @Test
        @DisplayName("AD 반영 실패는 재시도 가능한 코드로 바꾼다 — 형식 오류와 섞이면 관리자가 신청을 되돌린다")
        void adFailure_mapsToRetryableCode() {
            assertThat(codeOf(HttpStatus.INTERNAL_SERVER_ERROR, AD_FAILED))
                    .isEqualTo(ErrorCode.AD_GROUP_SYNC_FAILED);
        }

        @Test
        @DisplayName("원장에 없는 그룹은 GROUP_NOT_FOUND")
        void missingGroup_mapsToGroupNotFound() {
            assertThat(codeOf(HttpStatus.NOT_FOUND, GROUP_MISSING))
                    .isEqualTo(ErrorCode.GROUP_NOT_FOUND);
        }

        @Test
        @DisplayName("원장에 없는 계정은 INVALID_GROUP_MEMBER")
        void missingUser_mapsToInvalidGroupMember() {
            assertThat(codeOf(HttpStatus.NOT_FOUND, USER_MISSING))
                    .isEqualTo(ErrorCode.INVALID_GROUP_MEMBER);
        }

        @Test
        @DisplayName("모르는 4xx 는 인프라가 요청을 거절한 것으로, 5xx 는 호출 목적 실패로 가른다")
        void unknownErrors_fallBackToRejectedOr() {
            assertThat(codeOf(HttpStatus.BAD_REQUEST, "{\"error\":\"SOMETHING_NEW\"}"))
                    .isEqualTo(ErrorCode.INFRA_REQUEST_REJECTED);
            assertThat(codeOf(HttpStatus.SERVICE_UNAVAILABLE, "{\"error\":\"SOMETHING_NEW\"}"))
                    .isEqualTo(ErrorCode.GROUP_CREATION_FAILED);
        }

        @Test
        @DisplayName("본문이 비어도 터지지 않는다 — 502 응답에 본문이 없는 경우가 있다")
        void emptyBody_doesNotThrow() {
            assertThat(codeOf(HttpStatus.BAD_GATEWAY, "")).isEqualTo(ErrorCode.GROUP_CREATION_FAILED);
            assertThat(codeOf(HttpStatus.BAD_GATEWAY, null)).isEqualTo(ErrorCode.GROUP_CREATION_FAILED);
        }

        @Test
        @DisplayName("문장이 아니라 error 필드로 가른다 — detail 문구가 바뀌어도 판정이 유지된다")
        void branchesOnMachineCode_notProse() {
            String reworded =
                    "{\"detail\":\"문구가 완전히 바뀐 설명\",\"error\":\"AD_GROUP_MEMBER_FAILED\"}";
            assertThat(codeOf(HttpStatus.INTERNAL_SERVER_ERROR, reworded))
                    .isEqualTo(ErrorCode.AD_GROUP_SYNC_FAILED);
        }
    }

    @Nested
    @DisplayName("mapRemoveUserFromGroupError")
    class MapRemoveUserFromGroupError {

        private ErrorCode codeOf(HttpStatus status, String body) {
            return GroupService.mapRemoveUserFromGroupError(status, body).getErrorCode();
        }

        @Test
        @DisplayName("config-server remove_user_group 의 error 코드를 각각의 ErrorCode 로 바꾼다")
        void mapsMachineCodes() {
            assertThat(codeOf(HttpStatus.INTERNAL_SERVER_ERROR, "{\"error\":\"AD_GROUP_MEMBER_FAILED\"}"))
                    .isEqualTo(ErrorCode.AD_GROUP_REMOVE_FAILED);
            assertThat(codeOf(HttpStatus.CONFLICT, "{\"error\":\"PRIMARY_GROUP\"}"))
                    .isEqualTo(ErrorCode.PRIMARY_GROUP_REMOVAL);
            assertThat(codeOf(HttpStatus.NOT_FOUND, "{\"error\":\"GROUP_NOT_FOUND\"}"))
                    .isEqualTo(ErrorCode.GROUP_NOT_FOUND);
        }

        @Test
        @DisplayName("모르는 4xx 는 거절, 5xx 와 빈 본문은 제거 실패로 가른다")
        void unknownErrors_fallBackToRejectedOr() {
            assertThat(codeOf(HttpStatus.BAD_REQUEST, "{\"error\":\"INVALID_NAME\"}"))
                    .isEqualTo(ErrorCode.INFRA_REQUEST_REJECTED);
            assertThat(codeOf(HttpStatus.BAD_GATEWAY, null))
                    .isEqualTo(ErrorCode.GROUP_MEMBER_REMOVE_FAILED);
        }
    }

    @Nested
    @DisplayName("triggerNasGssFlush(admin_infra-proposed#161)")
    class TriggerNasGssFlush {

        @SuppressWarnings("unchecked")
        private WebClient.RequestHeadersSpec<?> mockPostChain() {
            WebClient.RequestBodyUriSpec uriSpec = mock(WebClient.RequestBodyUriSpec.class);
            WebClient.RequestHeadersSpec<?> headersSpec = mock(WebClient.RequestHeadersSpec.class);
            WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);
            doReturn(uriSpec).when(groupCreationWebClient).post();
            doReturn(headersSpec).when(uriSpec).uri("/operations/nas-gss-flush");
            doReturn(responseSpec).when(headersSpec).retrieve();
            doReturn(Mono.empty()).when(responseSpec).toBodilessEntity();
            return headersSpec;
        }

        @Test
        @DisplayName("성공하면 /operations/nas-gss-flush를 호출한다")
        void callsTheEndpoint() {
            mockPostChain();

            groupService.triggerNasGssFlush("testuser");

            verify(groupCreationWebClient).post();
        }

        @Test
        @DisplayName("호출이 실패해도 예외를 밖으로 던지지 않는다 — 30분 크론이 안전망이라 승인 흐름을 막으면 안 된다")
        void doesNotPropagateFailure() {
            doThrow(new RuntimeException("config-server 연결 실패")).when(groupCreationWebClient).post();

            assertThatCode(() -> groupService.triggerNasGssFlush("testuser"))
                    .doesNotThrowAnyException();
        }
    }
}
