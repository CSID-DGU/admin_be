package DGU_AI_LAB.admin_be.domain.alarm.service;

import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j; // Log4j2 -> Slf4j (Spring Boot 표준 권장)
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SlackApiService {

    @Value("${slack.bot-token}")
    private String botToken;
    private final RestTemplate restTemplate = new RestTemplate();

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
                // 필요 시 예외를 던져서 호출자에게 알림
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
    public void sendDM(User user) {
        String message = String.format("안녕하세요 %s님, 요청하신 GPU 서버 사용 기간이 만료되어 리소스가 정리되었습니다.", user.getName());
        this.sendDM(user.getName(), user.getEmail(), message);
    }

    public void sendDM(String username, String email, String message) {
        String userId = getSlackUser(username, email, botToken);
        if (userId == null) {
            // 여기서 throw하면 Worker나 Listener에서 잡힘
            throw new BusinessException(ErrorCode.SLACK_USER_NOT_FOUND);
        }

        String channelId = openDMChannel(userId, botToken);
        if (channelId == null) {
            throw new BusinessException(ErrorCode.SLACK_DM_CHANNEL_FAILED);
        }

        sendMessageToSlackChannel(channelId, message, botToken);
    }

    // --- Private Helper Methods  ---

    private String getSlackUser(String username, String email, String token) {
        String url = "https://slack.com/api/users.list";
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
            if (!Boolean.TRUE.equals(response.getBody().get("ok"))) {
                throw new BusinessException(ErrorCode.SLACK_USER_NOT_FOUND);
            }
            List<Map<String, Object>> members = (List<Map<String, Object>>) response.getBody().get("members");

            // 1차: 이름 매칭
            List<Map<String, Object>> matchedUsers = members.stream()
                    .filter(user -> {
                        Map<String, Object> profile = (Map<String, Object>) user.get("profile");
                        String displayName = (String) profile.get("display_name");
                        String realName = (String) profile.get("real_name");
                        String name = (String) user.get("name");
                        return (displayName != null && displayName.equals(username)) ||
                                (realName != null && realName.equals(username)) ||
                                (name != null && name.equals(username));
                    }).collect(Collectors.toList());

            if (matchedUsers.isEmpty()) throw new BusinessException(ErrorCode.SLACK_USER_NOT_FOUND);
            if (matchedUsers.size() == 1) return (String) matchedUsers.get(0).get("id");

            // (예외) 2차: 이메일 매칭
            Map<String, Object> selectedUser = matchedUsers.stream()
                    .filter(user -> {
                        Map<String, Object> profile = (Map<String, Object>) user.get("profile");
                        String userEmail = (String) profile.get("email");
                        return userEmail != null && userEmail.equalsIgnoreCase(email);
                    }).findFirst().orElseThrow(() -> new BusinessException(ErrorCode.SLACK_USER_EMAIL_NOT_MATCH));

            return (String) selectedUser.get("id");
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SLACK_USER_NOT_FOUND);
        }
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