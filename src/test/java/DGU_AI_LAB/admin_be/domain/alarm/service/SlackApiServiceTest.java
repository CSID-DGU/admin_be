package DGU_AI_LAB.admin_be.domain.alarm.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SlackApiServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    private SlackApiService slackApiService;

    @BeforeEach
    void setUp() {
        slackApiService = new SlackApiService(redisTemplate);
        ReflectionTestUtils.setField(slackApiService, "botToken", "test-bot-token");
        ReflectionTestUtils.setField(slackApiService, "restTemplate", restTemplate);
    }

    // Helper: mock users.list API to return one user
    private void mockUsersListApi(String userId, String username) {
        Map<String, Object> profile = new HashMap<>();
        profile.put("display_name", username);
        profile.put("real_name", username);
        profile.put("email", "test@example.com");

        Map<String, Object> user = new HashMap<>();
        user.put("id", userId);
        user.put("name", username);
        user.put("profile", profile);

        Map<String, Object> apiResponse = new HashMap<>();
        apiResponse.put("ok", true);
        apiResponse.put("members", List.of(user));

        ResponseEntity<Map> responseEntity = new ResponseEntity<>(apiResponse, HttpStatus.OK);

        when(valueOperations.get(anyString())).thenReturn(null);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(), eq(Map.class)))
                .thenReturn(responseEntity);
    }

    @Nested
    @DisplayName("openDMChannel - sendDM를 통해 간접 테스트")
    class OpenDMChannelTests {

        @Test
        @DisplayName("정상 응답 시 DM 전송 성공")
        void sendDM_success_whenChannelIdReturned() {
            // arrange
            mockUsersListApi("U123", "testuser");

            Map<String, Object> openChannelResponse = new HashMap<>();
            openChannelResponse.put("ok", true);
            Map<String, Object> channel = new HashMap<>();
            channel.put("id", "C123");
            openChannelResponse.put("channel", channel);

            Map<String, Object> postMessageResponse = new HashMap<>();
            postMessageResponse.put("ok", true);

            when(restTemplate.postForEntity(contains("conversations.open"), any(), eq(Map.class)))
                    .thenReturn(new ResponseEntity<>(openChannelResponse, HttpStatus.OK));
            when(restTemplate.postForEntity(contains("chat.postMessage"), any(), eq(Map.class)))
                    .thenReturn(new ResponseEntity<>(postMessageResponse, HttpStatus.OK));

            // act & assert - should not throw
            slackApiService.sendDM("testuser", "test@example.com", "hello");
        }

        @Test
        @DisplayName("conversations.open 응답 body가 null이면 NPE 없이 BusinessException 발생")
        void sendDM_nullBody_throwsBusinessExceptionWithoutNPE() {
            // arrange
            mockUsersListApi("U123", "testuser");

            when(restTemplate.postForEntity(contains("conversations.open"), any(), eq(Map.class)))
                    .thenReturn(new ResponseEntity<>(null, HttpStatus.OK));

            // act & assert
            assertThatThrownBy(() -> slackApiService.sendDM("testuser", "test@example.com", "hello"))
                    .isNotInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("conversations.open 응답에 channel 필드가 null이면 NPE 없이 BusinessException 발생")
        void sendDM_nullChannelInBody_throwsBusinessExceptionWithoutNPE() {
            // arrange
            mockUsersListApi("U123", "testuser");

            Map<String, Object> openChannelResponse = new HashMap<>();
            openChannelResponse.put("ok", true);
            openChannelResponse.put("channel", null);

            when(restTemplate.postForEntity(contains("conversations.open"), any(), eq(Map.class)))
                    .thenReturn(new ResponseEntity<>(openChannelResponse, HttpStatus.OK));

            // act & assert
            assertThatThrownBy(() -> slackApiService.sendDM("testuser", "test@example.com", "hello"))
                    .isNotInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("conversations.open 응답 ok=false이면 BusinessException 발생")
        void sendDM_okFalse_throwsBusinessException() {
            // arrange
            mockUsersListApi("U123", "testuser");

            Map<String, Object> openChannelResponse = new HashMap<>();
            openChannelResponse.put("ok", false);

            when(restTemplate.postForEntity(contains("conversations.open"), any(), eq(Map.class)))
                    .thenReturn(new ResponseEntity<>(openChannelResponse, HttpStatus.OK));

            // act & assert
            assertThatThrownBy(() -> slackApiService.sendDM("testuser", "test@example.com", "hello"))
                    .isNotInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("postToChannel")
    class PostToChannelTests {

        @SuppressWarnings("unchecked")
        private Map<String, Object> sentPayload() {
            ArgumentCaptor<HttpEntity<Map<String, Object>>> captor = ArgumentCaptor.forClass(HttpEntity.class);
            verify(restTemplate).postForEntity(contains("chat.postMessage"), captor.capture(), eq(Map.class));
            return captor.getValue().getBody();
        }

        @Test
        @DisplayName("채널에 올리고 Slack이 돌려준 메시지 식별자(ts)를 반환한다")
        void returnsTs() {
            when(restTemplate.postForEntity(contains("chat.postMessage"), any(), eq(Map.class)))
                    .thenReturn(new ResponseEntity<>(Map.of("ok", true, "ts", "1728200000.000100"), HttpStatus.OK));

            String ts = slackApiService.postToChannel("C0REQ", "신청서", null);

            assertThat(ts).isEqualTo("1728200000.000100");
            assertThat(sentPayload()).containsEntry("channel", "C0REQ").containsEntry("text", "신청서")
                    .doesNotContainKeys("thread_ts", "reply_broadcast", "blocks");
        }

        @Test
        @DisplayName("블록을 주면 글과 함께 싣고, 글은 알림 미리보기용으로 그대로 둔다")
        void sendsBlocksWithText() {
            when(restTemplate.postForEntity(contains("chat.postMessage"), any(), eq(Map.class)))
                    .thenReturn(new ResponseEntity<>(Map.of("ok", true, "ts", "1728200000.000100"), HttpStatus.OK));
            java.util.List<Map<String, Object>> blocks = java.util.List.of(Map.of("type", "divider"));

            slackApiService.postToChannel("C0REQ", "신청서", null, blocks);

            assertThat(sentPayload()).containsEntry("text", "신청서").containsEntry("blocks", blocks);
        }

        @Test
        @DisplayName("스레드 댓글은 원래 메시지를 가리키고 채널에도 함께 보이게 보낸다")
        void threadReplyIsBroadcast() {
            when(restTemplate.postForEntity(contains("chat.postMessage"), any(), eq(Map.class)))
                    .thenReturn(new ResponseEntity<>(Map.of("ok", true, "ts", "1728200099.000200"), HttpStatus.OK));

            slackApiService.postToChannel("C0REQ", "취소 알림", "1728200000.000100");

            assertThat(sentPayload()).containsEntry("thread_ts", "1728200000.000100")
                    .containsEntry("reply_broadcast", true);
        }

        @Test
        @DisplayName("Slack이 거절하거나(ok=false) 응답이 없거나 연결이 안 되면 BusinessException이다")
        void failuresBecomeBusinessException() {
            when(restTemplate.postForEntity(contains("chat.postMessage"), any(), eq(Map.class)))
                    .thenReturn(new ResponseEntity<>(Map.of("ok", false, "error", "not_in_channel"), HttpStatus.OK))
                    .thenReturn(new ResponseEntity<>(null, HttpStatus.OK))
                    .thenThrow(new org.springframework.web.client.ResourceAccessException("timeout"));

            for (int i = 0; i < 3; i++) {
                assertThatThrownBy(() -> slackApiService.postToChannel("C0REQ", "신청서", null))
                        .isInstanceOf(DGU_AI_LAB.admin_be.error.exception.BusinessException.class);
            }
        }
    }

    @Nested
    @DisplayName("findMembership")
    class FindMembership {

        private static final List<String> EMAILS = List.of("hong@dgu.ac.kr", "hong@gmail.com");

        private Map<String, Object> member(String displayName, String email, String flag) {
            Map<String, Object> profile = new HashMap<>();
            profile.put("display_name", displayName);
            if (email != null) {
                profile.put("email", email);
            }
            Map<String, Object> member = new HashMap<>();
            member.put("id", "U-" + displayName);
            member.put("name", "account-" + displayName);
            member.put("profile", profile);
            if (flag != null) {
                member.put(flag, true);
            }
            return member;
        }

        @SafeVarargs
        private ResponseEntity<Map> page(String nextCursor, Map<String, Object>... members) {
            Map<String, Object> body = new HashMap<>();
            body.put("ok", true);
            body.put("members", List.of(members));
            body.put("response_metadata", Map.of("next_cursor", nextCursor));
            return new ResponseEntity<>(body, HttpStatus.OK);
        }

        private void members(ResponseEntity<Map> onlyPage) {
            when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(), eq(Map.class))).thenReturn(onlyPage);
        }

        @BeforeEach
        void noCache() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString())).thenReturn(null);
        }

        @Test
        @DisplayName("다음 쪽에 있는 회원도 찾고, 탈퇴한 회원과 봇은 세지 않는다")
        void readsEveryPageAndSkipsInactive() {
            when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(), eq(Map.class)))
                    .thenReturn(page("abc=", member("탈퇴자", "hong@dgu.ac.kr", "deleted")))
                    .thenReturn(page("def=", member("알림봇", "hong@dgu.ac.kr", "is_bot")))
                    .thenReturn(page("", member("홍길동", "hong@dgu.ac.kr", null)));

            assertThat(slackApiService.findMembership("홍길동", EMAILS)).isEqualTo(SlackApiService.Membership.CONFIRMED);
            assertThat(slackApiService.findMembership("탈퇴자", EMAILS)).isEqualTo(SlackApiService.Membership.NOT_FOUND);
            assertThat(slackApiService.findMembership("알림봇", EMAILS)).isEqualTo(SlackApiService.Membership.NOT_FOUND);

            ArgumentCaptor<URI> urls = ArgumentCaptor.forClass(URI.class);
            verify(restTemplate, atLeast(3)).exchange(urls.capture(), eq(HttpMethod.GET), any(), eq(Map.class));
            assertThat(urls.getAllValues().subList(0, 3)).extracting(URI::getRawQuery)
                    .containsExactly("limit=1000", "limit=1000&cursor=abc%3D", "limit=1000&cursor=def%3D");
        }

        @Test
        @DisplayName("이메일은 주어진 것 가운데 하나와 같으면 되고 대소문자를 가리지 않는다")
        void matchesAnyGivenEmailIgnoringCase() {
            members(page("", member("홍길동", "Hong@Gmail.com", null)));

            assertThat(slackApiService.findMembership("홍길동", EMAILS)).isEqualTo(SlackApiService.Membership.CONFIRMED);
        }

        @Test
        @DisplayName("이름이 같아도 이메일이 다르면 없는 사람이고, 이메일이 같아도 이름이 다르면 없는 사람이다")
        void needsBothNameAndEmail() {
            members(page("", member("홍길동", "other@dgu.ac.kr", null), member("김철수", "hong@dgu.ac.kr", null)));

            assertThat(slackApiService.findMembership("홍길동", EMAILS)).isEqualTo(SlackApiService.Membership.NOT_FOUND);
        }

        @Test
        @DisplayName("Slack이 어느 회원의 이메일도 주지 않으면 이름만 보고 그 사실을 알린다")
        void nameOnlyWhenSlackGivesNoEmails() {
            members(page("", member("홍길동", null, null), member("김철수", null, null)));

            assertThat(slackApiService.findMembership("홍길동", EMAILS)).isEqualTo(SlackApiService.Membership.NAME_ONLY);
            assertThat(slackApiService.findMembership("이영희", EMAILS)).isEqualTo(SlackApiService.Membership.NOT_FOUND);
        }

        @Test
        @DisplayName("회원 목록을 받지 못하면 없다고 답하지 않고 예외를 낸다")
        void lookupFailureThrows() {
            when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(), eq(Map.class)))
                    .thenThrow(new RuntimeException("timeout"));

            assertThatThrownBy(() -> slackApiService.findMembership("홍길동", EMAILS))
                    .isInstanceOf(RuntimeException.class);
        }
    }
}
