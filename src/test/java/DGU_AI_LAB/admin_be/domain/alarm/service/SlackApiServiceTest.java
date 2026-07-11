package DGU_AI_LAB.admin_be.domain.alarm.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(Map.class)))
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
}
