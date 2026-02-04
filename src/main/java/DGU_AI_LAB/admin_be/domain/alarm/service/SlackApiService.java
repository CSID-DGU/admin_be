package DGU_AI_LAB.admin_be.domain.alarm.service;

import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 실제 Slack API와 통신하는 transport 담당입니다.
 * 관심 있음: 어떻게 보내는지, 누구인지
 * 관심 없음: 누구한테 왜 보냈는지
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SlackApiService {

    @Value("${slack.bot-token}")
    private String botToken;

    private final RestTemplate restTemplate = new RestTemplate();
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String SLACK_USERS_CACHE_KEY = "slack:cache:users:list";
    private static final long CACHE_TTL_HOURS = 1;

    // =========================================================================
    // 1. Webhook 전송
    // =========================================================================
    public void sendWebhook(String webhookUrl, String message) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, String> payload = Map.of("text", message);
        HttpEntity<Map<String, String>> request = new HttpEntity<>(payload, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(webhookUrl, request, String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                log.warn("Slack Webhook 전송 응답 이상: {}", response.getStatusCode());
                throw new BusinessException(ErrorCode.SLACK_SEND_FAILED);
            }
        } catch (Exception e) {
            log.error("Slack Webhook 전송 실패 (URL: {}): {}", webhookUrl, e.getMessage());
            throw new BusinessException(ErrorCode.SLACK_SEND_FAILED);
        }
    }

    // =========================================================================
    // 2. DM 전송
    // =========================================================================

    public void sendDM(String username, String email, String message) {
        String userId = getSlackUserId(username, email);

        if (userId == null) {
            log.warn("Slack User Not Found: username={}, email={}", username, email);
            throw new BusinessException(ErrorCode.SLACK_USER_NOT_FOUND);
        }

        String channelId = openDMChannel(userId, botToken);
        if (channelId == null) {
            throw new BusinessException(ErrorCode.SLACK_DM_CHANNEL_FAILED);
        }

        sendMessageToSlackChannel(channelId, message, botToken);
    }

    /**
     * 관리자용: Slack 사용자 캐시 강제 새로고침
     * (신규 사용자 발생 시 Admin API 등을 통해 호출)
     */
    public void refreshSlackUserCache() {
        redisTemplate.delete(SLACK_USERS_CACHE_KEY);
        getSlackMembersWithCache(); // 즉시 재호출하여 캐시 워밍
        log.info("Slack User Cache 강제 초기화 완료");
    }

    // --- Private Helper Methods ---

    @SuppressWarnings("unchecked") // IDE에서 Redis 캐스팅 경고를 억제하기 위해서 추가 (깔끔함용)
    private List<Map<String, Object>> getSlackMembersWithCache() {
        try {
            Object cachedData = redisTemplate.opsForValue().get(SLACK_USERS_CACHE_KEY);
            if (cachedData != null) {
                log.debug("Slack User List: Redis 캐시 히트");
                return (List<Map<String, Object>>) cachedData;
            }
        } catch (Exception e) {
            log.warn("Redis 조회 실패, API 직접 호출 진행: {}", e.getMessage());
        }

        log.info("Slack User List: API 직접 호출 (Refresh)");
        String url = "https://slack.com/api/users.list?limit=1000";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(botToken);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
            if (!Boolean.TRUE.equals(response.getBody().get("ok"))) {
                throw new BusinessException(ErrorCode.SLACK_USER_NOT_FOUND);
            }

            List<Map<String, Object>> members = (List<Map<String, Object>>) response.getBody().get("members");

            // Redis 저장
            try {
                redisTemplate.opsForValue().set(SLACK_USERS_CACHE_KEY, members, Duration.ofHours(CACHE_TTL_HOURS));
            } catch (Exception e) {
                log.error("Redis 저장 실패: {}", e.getMessage());
            }

            return members;

        } catch (Exception e) {
            log.error("Slack users.list API 호출 실패", e);
            throw new BusinessException(ErrorCode.SLACK_USER_NOT_FOUND);
        }
    }

    private String getSlackUserId(String username, String email) {
        List<Map<String, Object>> members = getSlackMembersWithCache();

        // 1차: 이름 매칭
        List<Map<String, Object>> matchedUsers = members.stream()
                .filter(user -> {
                    Map<String, Object> profile = (Map<String, Object>) user.get("profile");
                    if (profile == null) return false;

                    String displayName = (String) profile.get("display_name");
                    String realName = (String) profile.get("real_name");
                    String name = (String) user.get("name");

                    return (displayName != null && displayName.equals(username)) ||
                            (realName != null && realName.equals(username)) ||
                            (name != null && name.equals(username));
                }).collect(Collectors.toList());

        if (matchedUsers.isEmpty()) return null;
        if (matchedUsers.size() == 1) return (String) matchedUsers.get(0).get("id");

        // 2차: 이메일 매칭
        Map<String, Object> selectedUser = matchedUsers.stream()
                .filter(user -> {
                    Map<String, Object> profile = (Map<String, Object>) user.get("profile");
                    String userEmail = (String) profile.get("email");
                    return userEmail != null && userEmail.equalsIgnoreCase(email);
                }).findFirst().orElse(null);

        return selectedUser != null ? (String) selectedUser.get("id") : null;
    }

    private String openDMChannel(String userId, String token) {
        String url = "https://slack.com/api/conversations.open";
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(Map.of("users", userId), headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            if (Boolean.TRUE.equals(response.getBody().get("ok"))) {
                Map channel = (Map) response.getBody().get("channel");
                return (String) channel.get("id");
            }
        } catch (Exception e) {
            log.error("DM 채널 오픈 API 오류", e);
        }
        return null;
    }

    private void sendMessageToSlackChannel(String channelId, String message, String token) {
        String url = "https://slack.com/api/chat.postMessage";
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(Map.of("channel", channelId, "text", message), headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            if (!Boolean.TRUE.equals(response.getBody().get("ok"))) {
                log.error("Slack 메시지 전송 실패: {}", response.getBody().get("error"));
                throw new BusinessException(ErrorCode.SLACK_SEND_FAILED);
            }
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SLACK_SEND_FAILED);
        }
    }
}