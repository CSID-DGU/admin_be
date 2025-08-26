package DGU_AI_LAB.admin_be.domain.alarm.service;

import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Log4j2
public class AlarmService {

    @Value("${slack-webhook-url.monitoring}")
    private String defaultWebhookUrl;

    @Value("${slack.bot-token}")
    private String botToken;
    private final RestTemplate restTemplate = new RestTemplate();

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String from;

    public void sendSlackAlert(String message) {
        sendSlackAlert(message, null);
    }

    public void sendSlackAlert(String message, String webhookUrl) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> payload = Map.of("text", message);
        HttpEntity<Map<String, String>> request = new HttpEntity<>(payload, headers);

        String urlToUse = (webhookUrl != null && !webhookUrl.isEmpty()) ? webhookUrl : defaultWebhookUrl;

        ResponseEntity<String> response = restTemplate.postForEntity(urlToUse, request, String.class);

        if (!response.getStatusCode().is2xxSuccessful()) {
            log.debug("Slack 알림 전송 실패: {}", response.getStatusCode());
        } else {
            log.debug("Slack 알림 전송 성공");
        }
    }
    public void sendMailAlert(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
        System.out.printf("메일 전송 성공: 수신자=%s, 제목=%s%n", to, subject);
    }

    // slack dm 전송
    public void sendDMAlert(String username, String email, String message) {
        String userId = getSlackUser(username, email, botToken);
        if (userId == null) {
            throw new BusinessException(ErrorCode.SLACK_USER_NOT_FOUND);
        }

        String channelId = openDMChannel(userId, botToken);
        if (channelId == null) {
            throw new BusinessException(ErrorCode.SLACK_DM_CHANNEL_FAILED);
        }

        try {
            sendMessageToSlackChannel(channelId, message, botToken);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SLACK_SEND_FAILED);
        }
    }

    // 이름이 일치하는 사용자에게 dm 전송
    // 이름이 같은 사용자가 있는 경우 email이 일치하는 사용자에게 dm 전송
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

                    return username.equals(name) || username.equals(displayName) || username.equals(realName);
                })
                .collect(Collectors.toList());

        if (matchedUsers.isEmpty()) {
            throw new BusinessException(ErrorCode.SLACK_USER_NOT_FOUND);
        }

        if (matchedUsers.size() == 1) {
            return (String) matchedUsers.get(0).get("id");
        }

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
            throw new BusinessException(ErrorCode.SLACK_SEND_FAILED);
        }
    }

    public void sendAllAlerts(String username, String email, String subject, String message) {
        sendDMAlert(username, email, message);
        sendMailAlert(email, subject, message);
    }
}