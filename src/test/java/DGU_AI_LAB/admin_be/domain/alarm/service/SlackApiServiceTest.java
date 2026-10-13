package DGU_AI_LAB.admin_be.domain.alarm.service;

import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.ExpectedCount.manyTimes;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(MockitoExtension.class)
class SlackApiServiceTest {

    private static final String USERS_LIST = "https://slack.com/api/users.list?limit=1000";
    private static final String WEBHOOK_URL = "https://hooks.slack.test/services/T000/B000/token";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    private MockRestServiceServer server;
    private SlackApiService slackApiService;

    /** 주소에 그 글자가 들어간 요청으로 나간 본문을 순서대로 모은다. */
    private final Map<String, List<String>> sentBodies = new HashMap<>();

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
        slackApiService = new SlackApiService(redisTemplate, builder.build());
        ReflectionTestUtils.setField(slackApiService, "botToken", "test-bot-token");
    }

    private static String json(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static ResponseCreator ok(Object body) {
        return withSuccess(json(body), MediaType.APPLICATION_JSON);
    }

    /** 봇 토큰을 실은 POST만 받고, 나간 본문을 기록한다. */
    private void onPost(String urlPart, ExpectedCount count, ResponseCreator response) {
        server.expect(count, requestTo(containsString(urlPart)))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-bot-token"))
                .andExpect(request -> sentBodies.computeIfAbsent(urlPart, key -> new ArrayList<>())
                        .add(((MockClientHttpRequest) request).getBodyAsString()))
                .andRespond(response);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> onlyBody(String urlPart) {
        List<String> bodies = sentBodies.getOrDefault(urlPart, List.of());
        assertThat(bodies).hasSize(1);
        try {
            return JSON.readValue(bodies.get(0), Map.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private void noCache() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
    }

    // Helper: users.list API가 회원 한 명을 돌려준다
    private void mockUsersListApi(String userId, String username) {
        Map<String, Object> profile = new HashMap<>();
        profile.put("display_name", username);
        profile.put("real_name", username);
        profile.put("email", "test@example.com");

        Map<String, Object> user = new HashMap<>();
        user.put("id", userId);
        user.put("name", username);
        user.put("profile", profile);

        noCache();
        server.expect(manyTimes(), requestTo(USERS_LIST))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-bot-token"))
                .andRespond(ok(Map.of("ok", true, "members", List.of(user))));
    }

    @Nested
    @DisplayName("sendWebhook")
    class SendWebhookTests {

        @Test
        @DisplayName("글과 블록을 JSON으로 보낸다")
        void sendsTextAndBlocks() throws Exception {
            List<String> bodies = new ArrayList<>();
            server.expect(once(), requestTo(WEBHOOK_URL))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
                    .andExpect(request -> bodies.add(((MockClientHttpRequest) request).getBodyAsString()))
                    .andRespond(withSuccess("ok", MediaType.TEXT_HTML));
            List<Map<String, Object>> blocks = List.of(Map.of("type", "divider"));

            slackApiService.sendWebhook(WEBHOOK_URL, "안내", blocks);

            server.verify();
            assertThat(JSON.readValue(bodies.get(0), Map.class))
                    .containsEntry("text", "안내").containsEntry("blocks", blocks);
        }

        @Test
        @DisplayName("웹훅이 지워졌거나 권한이 없으면 설정 문제로, 그 밖의 거절과 서버 오류는 전송 실패로 알린다")
        void mapsStatusToErrorCode() {
            for (HttpStatus dead : List.of(HttpStatus.NOT_FOUND, HttpStatus.GONE, HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN)) {
                assertWebhookFailure(dead, ErrorCode.SLACK_CONFIG_DEAD);
            }
            assertWebhookFailure(HttpStatus.BAD_REQUEST, ErrorCode.SLACK_SEND_FAILED);
            assertWebhookFailure(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.SLACK_SEND_FAILED);
        }

        private void assertWebhookFailure(HttpStatus status, ErrorCode expected) {
            server.reset();
            server.expect(once(), requestTo(WEBHOOK_URL)).andRespond(withStatus(status));

            assertThatThrownBy(() -> slackApiService.sendWebhook(WEBHOOK_URL, "안내"))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", expected);
        }
    }

    @Nested
    @DisplayName("openDMChannel - sendDM를 통해 간접 테스트")
    class OpenDMChannelTests {

        @Test
        @DisplayName("정상 응답 시 DM 전송 성공")
        void sendDM_success_whenChannelIdReturned() {
            mockUsersListApi("U123", "testuser");
            onPost("conversations.open", once(), ok(Map.of("ok", true, "channel", Map.of("id", "C123"))));
            onPost("chat.postMessage", once(), ok(Map.of("ok", true)));

            slackApiService.sendDM("testuser", List.of("test@example.com"), "hello");

            server.verify();
            assertThat(onlyBody("conversations.open")).containsEntry("users", "U123");
            assertThat(onlyBody("chat.postMessage")).containsEntry("channel", "C123").containsEntry("text", "hello");
        }

        @Test
        @DisplayName("conversations.open 응답 body가 없으면 NPE 없이 BusinessException 발생")
        void sendDM_nullBody_throwsBusinessExceptionWithoutNPE() {
            mockUsersListApi("U123", "testuser");
            onPost("conversations.open", once(), withSuccess());

            assertThatThrownBy(() -> slackApiService.sendDM("testuser", List.of("test@example.com"), "hello"))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SLACK_DM_CHANNEL_FAILED);
        }

        @Test
        @DisplayName("conversations.open 응답에 channel 필드가 null이면 NPE 없이 BusinessException 발생")
        void sendDM_nullChannelInBody_throwsBusinessExceptionWithoutNPE() {
            mockUsersListApi("U123", "testuser");
            onPost("conversations.open", once(), withSuccess("{\"ok\":true,\"channel\":null}", MediaType.APPLICATION_JSON));

            assertThatThrownBy(() -> slackApiService.sendDM("testuser", List.of("test@example.com"), "hello"))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SLACK_DM_CHANNEL_FAILED);
        }

        @Test
        @DisplayName("conversations.open 응답 ok=false이면 BusinessException 발생")
        void sendDM_okFalse_throwsBusinessException() {
            mockUsersListApi("U123", "testuser");
            onPost("conversations.open", once(), ok(Map.of("ok", false)));

            assertThatThrownBy(() -> slackApiService.sendDM("testuser", List.of("test@example.com"), "hello"))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SLACK_DM_CHANNEL_FAILED);
        }
    }

    @Nested
    @DisplayName("postToChannel")
    class PostToChannelTests {

        @Test
        @DisplayName("채널에 올리고 Slack이 돌려준 메시지 식별자(ts)를 반환한다")
        void returnsTs() {
            onPost("chat.postMessage", once(), ok(Map.of("ok", true, "ts", "1728200000.000100")));

            String ts = slackApiService.postToChannel("C0REQ", "신청서", null);

            assertThat(ts).isEqualTo("1728200000.000100");
            assertThat(onlyBody("chat.postMessage")).containsEntry("channel", "C0REQ").containsEntry("text", "신청서")
                    .doesNotContainKeys("thread_ts", "reply_broadcast", "blocks");
        }

        @Test
        @DisplayName("블록을 주면 글과 함께 싣고, 글은 알림 미리보기용으로 그대로 둔다")
        void sendsBlocksWithText() {
            onPost("chat.postMessage", once(), ok(Map.of("ok", true, "ts", "1728200000.000100")));
            List<Map<String, Object>> blocks = List.of(Map.of("type", "divider"));

            slackApiService.postToChannel("C0REQ", "신청서", null, blocks);

            assertThat(onlyBody("chat.postMessage")).containsEntry("text", "신청서").containsEntry("blocks", blocks);
        }

        @Test
        @DisplayName("스레드 댓글은 원래 메시지를 가리키고 채널에도 함께 보이게 보낸다")
        void threadReplyIsBroadcast() {
            onPost("chat.postMessage", once(), ok(Map.of("ok", true, "ts", "1728200099.000200")));

            slackApiService.postToChannel("C0REQ", "취소 알림", "1728200000.000100");

            assertThat(onlyBody("chat.postMessage")).containsEntry("thread_ts", "1728200000.000100")
                    .containsEntry("reply_broadcast", true);
        }

        @Test
        @DisplayName("Slack이 거절하거나(ok=false) 응답이 없거나 연결이 안 되거나 오류 상태로 답하면 BusinessException이다")
        void failuresBecomeBusinessException() {
            List<ResponseCreator> failures = List.of(
                    ok(Map.of("ok", false, "error", "not_in_channel")),
                    withSuccess(),
                    withException(new IOException("timeout")),
                    withStatus(HttpStatus.TOO_MANY_REQUESTS),
                    withStatus(HttpStatus.BAD_GATEWAY));

            for (ResponseCreator failure : failures) {
                server.reset();
                onPost("chat.postMessage", once(), failure);

                assertThatThrownBy(() -> slackApiService.postToChannel("C0REQ", "신청서", null))
                        .isInstanceOf(BusinessException.class)
                        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SLACK_SEND_FAILED);
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
        private ResponseCreator page(String nextCursor, Map<String, Object>... members) {
            Map<String, Object> body = new HashMap<>();
            body.put("ok", true);
            body.put("members", List.of(members));
            body.put("response_metadata", Map.of("next_cursor", nextCursor));
            return ok(body);
        }

        private void members(ResponseCreator onlyPage) {
            server.expect(manyTimes(), requestTo(USERS_LIST)).andRespond(onlyPage);
        }

        private void dmChannelOpens(ExpectedCount count) {
            onPost("conversations.open", count, ok(Map.of("ok", true, "channel", Map.of("id", "D1"))));
            onPost("chat.postMessage", count, ok(Map.of("ok", true)));
        }

        @BeforeEach
        void noCacheForEach() {
            noCache();
        }

        @Test
        @DisplayName("다음 쪽에 있는 회원도 찾고, 탈퇴한 회원과 봇은 세지 않는다")
        void readsEveryPageAndSkipsInactive() {
            // 주소가 글자 그대로 같아야 통과한다 — cursor 끝의 '='가 %3D로 한 번만 바뀌어 나가는지도 함께 본다.
            server.expect(manyTimes(), requestTo(USERS_LIST))
                    .andRespond(page("abc=", member("탈퇴자", "hong@dgu.ac.kr", "deleted")));
            server.expect(manyTimes(), requestTo(USERS_LIST + "&cursor=abc%3D"))
                    .andRespond(page("def=", member("알림봇", "hong@dgu.ac.kr", "is_bot")));
            server.expect(manyTimes(), requestTo(USERS_LIST + "&cursor=def%3D"))
                    .andRespond(page("", member("홍길동", "hong@dgu.ac.kr", null)));

            assertThat(slackApiService.findMembership("홍길동", EMAILS)).isEqualTo(SlackApiService.Membership.CONFIRMED);
            assertThat(slackApiService.findMembership("탈퇴자", EMAILS)).isEqualTo(SlackApiService.Membership.NOT_FOUND);
            assertThat(slackApiService.findMembership("알림봇", EMAILS)).isEqualTo(SlackApiService.Membership.NOT_FOUND);
            server.verify();
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
        @DisplayName("DM은 이름과 이메일이 모두 같은 회원에게 보낸다 — 이름이 같은 사람이 한 명뿐이어도 이메일이 다르면 보내지 않는다")
        void dmNeedsBothNameAndEmail() {
            members(page("", member("홍길동", "other@dgu.ac.kr", null)));

            // POST 기대를 걸지 않았으므로, 전송을 시도하면 예상 밖 요청으로 실패한다.
            assertThatThrownBy(() -> slackApiService.sendDM("홍길동", EMAILS, "안내"))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SLACK_USER_NOT_FOUND);
        }

        @Test
        @DisplayName("DM은 탈퇴한 회원과 봇을 건너뛰고 같은 이름의 활동 회원에게 보낸다")
        void dmSkipsInactiveMembers() {
            Map<String, Object> active = member("홍길동", "hong@gmail.com", null);
            active.put("id", "U-active");
            members(page("", member("홍길동", "hong@dgu.ac.kr", "deleted"), active));
            dmChannelOpens(once());

            slackApiService.sendDM("홍길동", EMAILS, "안내");

            assertThat(onlyBody("conversations.open")).containsEntry("users", "U-active");
        }

        @Test
        @DisplayName("Slack이 이메일을 주지 않으면 그 이름의 회원이 한 명일 때만 DM을 보낸다")
        void dmByNameOnlyWhenSlackGivesNoEmails() {
            members(page("", member("홍길동", null, null), member("김철수", null, null), member("김철수", null, null)));
            dmChannelOpens(once());

            slackApiService.sendDM("홍길동", EMAILS, "안내");
            assertThatThrownBy(() -> slackApiService.sendDM("김철수", EMAILS, "안내"))
                    .isInstanceOf(BusinessException.class);

            server.verify();
            assertThat(onlyBody("conversations.open")).containsEntry("users", "U-홍길동");
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
            members(withException(new IOException("timeout")));

            assertThatThrownBy(() -> slackApiService.findMembership("홍길동", EMAILS))
                    .isInstanceOf(BusinessException.class);
        }
    }
}
