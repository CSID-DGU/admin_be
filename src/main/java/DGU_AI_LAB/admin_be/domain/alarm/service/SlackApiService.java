package DGU_AI_LAB.admin_be.domain.alarm.service;

import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Slack Bot Token을 사용하여 Slack API와 직접 통신하는 서비스
 * (DM 전송 등)
 */
@Service
@RequiredArgsConstructor
@Log4j2
public class SlackApiService {

    @Value("${slack.bot-token}")
    private String botToken;
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 사용자에게 Slack DM을 전송합니다.
     */
    public void sendDM(String username, String email, String message) {
        String userId = getSlackUser(username, email, botToken);
        if (userId == null) {
            log.warn("Slack DM 전송 실패: 사용자를 찾을 수 없습니다. (이름: {}, 이메일: {})", username, email);
            throw new BusinessException(ErrorCode.SLACK_USER_NOT_FOUND);
        }

        String channelId = openDMChannel(userId, botToken);
        if (channelId == null) {
            log.warn("Slack DM 채널 오픈 실패: (사용자 ID: {})", userId);
            throw new BusinessException(ErrorCode.SLACK_DM_CHANNEL_FAILED);
        }

        try {
            sendMessageToSlackChannel(channelId, message, botToken);
        } catch (Exception e) {
            log.error("Slack DM 전송 중 오류 발생", e);
            throw new BusinessException(ErrorCode.SLACK_SEND_FAILED);
        }
    }

    /**
     * 이름과 이메일로 Slack 사용자 ID를 찾습니다.
     */
    private String getSlackUser(String username, String email, String token) {
        String url = "https://slack.com/api/users.list";
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
        if (!Boolean.TRUE.equals(response.getBody().get("ok"))) {
            throw new BusinessException(ErrorCode.SLACK_USER_NOT_FOUND);
        }

        List<Map<String, Object>> members = (List<Map<String, Object>>) response.getBody().get("members");

        // 이름이 일치하는 사용자 목록 필터링
        List<Map<String, Object>> matchedUsers = members.stream()
                .filter(user -> {
                    Map<String, Object> profile = (Map<String, Object>) user.get("profile");
                    String displayName = (String) profile.get("display_name");
                    String realName = (String) profile.get("real_name");
                    String name = (String) user.get("name");

                    // 하나라도 null이 아닌 이름 필드가 username과 일치하는지 확인
                    return (displayName != null && displayName.equals(username)) ||
                            (realName != null && realName.equals(username)) ||
                            (name != null && name.equals(username));
                })
                .collect(Collectors.toList());

        if (matchedUsers.isEmpty()) {
            throw new BusinessException(ErrorCode.SLACK_USER_NOT_FOUND);
        }

        if (matchedUsers.size() == 1) {
            return (String) matchedUsers.get(0).get("id");
        }

        // 이름이 중복될 경우, 이메일로 재검색
        Map<String, Object> selectedUser = matchedUsers.stream()
                .filter(user -> {
                    Map<String, Object> profile = (Map<String, Object>) user.get("profile");
                    String userEmail = (String) profile.get("email");
                    return userEmail != null && userEmail.equalsIgnoreCase(email);
                })
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.SLACK_USER_EMAIL_NOT_MATCH));

        return (String) selectedUser.get("id");
    }

    /**
     * 사용자 ID로 DM 채널 ID를 엽니다.
     */
    private String openDMChannel(String userId, String token) {
        String url = "https://slack.com/api/conversations.open";
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of("users", userId);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
        if (Boolean.TRUE.equals(response.getBody().get("ok"))) {
            Map channel = (Map) response.getBody().get("channel");
            return (String) channel.get("id");
        }
        return null;
    }

    /**
     * 채널 ID로 메시지를 전송합니다. (DM, 공개채널 공용)
     */
    private void sendMessageToSlackChannel(String channelId, String message, String token) {
        String url = "https://slack.com/api/chat.postMessage";
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "channel", channelId,
                "text", message
        );
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
        if (!Boolean.TRUE.equals(response.getBody().get("ok"))) {
            log.error("Slack 메시지 전송 실패 (채널 ID: {}): {}", channelId, response.getBody().get("error"));
            throw new BusinessException(ErrorCode.SLACK_SEND_FAILED);
        }
    }
}