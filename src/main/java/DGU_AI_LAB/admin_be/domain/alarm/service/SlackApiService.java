package DGU_AI_LAB.admin_be.domain.alarm.service;

import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import DGU_AI_LAB.admin_be.global.util.MessageUtils;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class SlackApiService {

    /**
     * 모든 클래스에서 알림에 들어갈 메시지는 MessageUtil에서 관리하고 있어요.
     * 알림 문구를 수정하려면, resources/messages.properties에서 수정해주세요.
     */

    @Value("${slack.bot-token}")
    private String botToken;

    private final RestTemplate restTemplate = new RestTemplate();
    private final RedisTemplate<String, Object> redisTemplate;
    private final MessageUtils messageUtils;

    private static final String SLACK_USERS_CACHE_KEY = "slack:cache:users:list";
    private static final long CACHE_TTL_HOURS = 1; // 캐시 유지 시간 (1시간)

    // =========================================================================
    // 1. Webhook 전송 (관리자 알림용)
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
    // 2. DM 전송 (사용자 알림용)
    // =========================================================================
    public void sendExpiredDM(User user) {
        String message = messageUtils.get("notification.expired.dm", user.getName());
        this.sendDM(user.getName(), user.getEmail(), message);
    }

    public void sendDM(String username, String email, String message) {
        String userId = getSlackUserId(username, email);

        if (userId == null) {
            throw new BusinessException(ErrorCode.SLACK_USER_NOT_FOUND);
        }

        String channelId = openDMChannel(userId, botToken);
        if (channelId == null) {
            throw new BusinessException(ErrorCode.SLACK_DM_CHANNEL_FAILED);
        }
        sendMessageToSlackChannel(channelId, message, botToken);
    }

    // --- Private Helper Methods ---
    /**
     * Redis 캐싱을 적용하여 Slack User 목록 조회
     * 1. Redis 조회 -> 2. 없으면 API 호출 -> 3. Redis 저장
     */
    private List<Map<String, Object>> getSlackMembersWithCache() {
        try {
            // 1. Redis 캐시 조회
            Object cachedData = redisTemplate.opsForValue().get(SLACK_USERS_CACHE_KEY);
            if (cachedData != null) {
                log.debug("Slack User List: Redis 캐시 히트 (API 호출 생략)");
                return (List<Map<String, Object>>) cachedData;
            }
        } catch (Exception e) {
            log.warn("Redis 조회 실패, API 직접 호출 진행: {}", e.getMessage());
        }

        // 2. 캐시가 없으면 API 호출
        log.info("Slack User List: API 직접 호출 (Refresh)");
        String url = "https://slack.com/api/users.list";
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(botToken);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
            if (!Boolean.TRUE.equals(response.getBody().get("ok"))) {
                throw new BusinessException(ErrorCode.SLACK_USER_NOT_FOUND);
            }

            List<Map<String, Object>> members = (List<Map<String, Object>>) response.getBody().get("members");

            // 3. Redis에 저장 (1시간 유지)
            try {
                redisTemplate.opsForValue().set(SLACK_USERS_CACHE_KEY, members, Duration.ofHours(CACHE_TTL_HOURS));
            } catch (Exception e) {
                log.error("Redis 저장 실패 (기능은 계속 수행됨): {}", e.getMessage());
            }

            return members;

        } catch (Exception e) {
            log.error("Slack users.list API 호출 실패", e);
            throw new BusinessException(ErrorCode.SLACK_USER_NOT_FOUND);
        }
    }

    /**
     * 캐시된 목록에서 이름/이메일로 사용자 ID 매칭
     */
    private String getSlackUserId(String username, String email) {
        // 캐시 적용된 목록 가져오기
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

        if (matchedUsers.isEmpty()) return null; // 상위에서 Exception 처리
        if (matchedUsers.size() == 1) return (String) matchedUsers.get(0).get("id");

        // 2차: 이메일 매칭 (동명이인 또는 사용자가 이름을 잘못 저장했을 경우 처리)
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