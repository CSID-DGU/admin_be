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
    @DisplayName("hasMemberNamed")
    class HasMemberNamed {

        private Map<String, Object> member(String displayName, String flag) {
            Map<String, Object> profile = new HashMap<>();
            profile.put("display_name", displayName);
            Map<String, Object> member = new HashMap<>();
            member.put("id", "U-" + displayName);
            member.put("name", "account-" + displayName);
            member.put("profile", profile);
            if (flag != null) {
                member.put(flag, true);
            }
            return member;
        }

        private ResponseEntity<Map> page(String nextCursor, Map<String, Object> member) {
            Map<String, Object> body = new HashMap<>();
            body.put("ok", true);
            body.put("members", List.of(member));
            body.put("response_metadata", Map.of("next_cursor", nextCursor));
            return new ResponseEntity<>(body, HttpStatus.OK);
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
                    .thenReturn(page("abc=", member("탈퇴자", "deleted")))
                    .thenReturn(page("def=", member("알림봇", "is_bot")))
                    .thenReturn(page("", member("홍길동", null)));

            assertThat(slackApiService.hasMemberNamed("홍길동")).isTrue();
            assertThat(slackApiService.hasMemberNamed("탈퇴자")).isFalse();
            assertThat(slackApiService.hasMemberNamed("알림봇")).isFalse();

            ArgumentCaptor<URI> urls = ArgumentCaptor.forClass(URI.class);
            verify(restTemplate, atLeast(3)).exchange(urls.capture(), eq(HttpMethod.GET), any(), eq(Map.class));
            assertThat(urls.getAllValues().subList(0, 3)).extracting(URI::getRawQuery)
                    .containsExactly("limit=1000", "limit=1000&cursor=abc%3D", "limit=1000&cursor=def%3D");
        }

        @Test
        @DisplayName("회원 목록을 받지 못하면 없다고 답하지 않고 예외를 낸다")
        void lookupFailureThrows() {
            when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(), eq(Map.class)))
                    .thenThrow(new RuntimeException("timeout"));

            assertThatThrownBy(() -> slackApiService.hasMemberNamed("홍길동"))
                    .isInstanceOf(RuntimeException.class);
        }
    }
}
