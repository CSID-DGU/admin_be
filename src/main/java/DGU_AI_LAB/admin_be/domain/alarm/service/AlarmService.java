package DGU_AI_LAB.admin_be.domain.alarm.service;

import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
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
    @Value("${slack-webhook-url.farm-admin}")
    private String farmAdminWebhookUrl;
    @Value("${slack-webhook-url.lab-admin}")
    private String labAdminWebhookUrl;


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

    public void sendNewRequestNotification(Request request) {
        String serverName = request.getResourceGroup().getServerName();
        String targetWebhookUrl;

        // serverName에 따라 사용할 다른 채널로 전송
        if ("FARM".equalsIgnoreCase(serverName)) {
            targetWebhookUrl = farmAdminWebhookUrl;
        } else if ("LAB".equalsIgnoreCase(serverName)) {
            targetWebhookUrl = labAdminWebhookUrl;
        } else {
            // FARM이나 LAB이 아닌 잘못된 입력값이 있을 경우, 기본 모니터링 채널로 전송
            log.warn("알 수 없는 serverName '{}'에 대한 요청 알림입니다. 기본 채널로 전송합니다.", serverName);
            targetWebhookUrl = defaultWebhookUrl;
        }

        // 슬랙 메시지 내용을 생성합니다.
        String message = String.format(
                "🔔 새로운 서버 사용 신청이 도착했습니다! 🔔\n" +
                        "------------------------------------------\n" +
                        "▶ 신청자: %s (%s)\n" +
                        "▶ 신청 서버: %s\n" +
                        "▶ Ubuntu 사용자 이름: %s\n" +
                        "▶ 요청 이미지: %s:%s\n" +
                        "▶ 요청 볼륨: %dGiB\n" +
                        "------------------------------------------\n" +
                        "관리자 페이지에서 확인 후 승인해 주세요.",
                request.getUser().getName(),
                request.getUser().getStudentId(),
                serverName,
                request.getUbuntuUsername(),
                request.getContainerImage().getImageName(),
                request.getContainerImage().getImageVersion(),
                request.getVolumeSizeGiB()
        );

        sendSlackAlert(message, targetWebhookUrl);
    }

    /**
     * 사용자에게 서버 사용 신청 승인 알림을 보냅니다.
     * @param request 승인된 Request 엔티티
     */
    public void sendApprovalNotification(Request request) {
        User user = request.getUser();
        String subject = "[DGU AI LAB] 서버 사용 신청이 승인되었습니다.";
        String message = String.format(
                """
                🎉 %s님의 서버 사용 신청이 성공적으로 승인되었습니다! 🎉
                
                아래 정보를 사용하여 서버에 접속해 주세요.
                -------------------------------------
                - Ubuntu 사용자 이름: %s
                - 할당된 서버: %s
                - 컨테이너 이미지: %s:%s
                - 할당된 볼륨 크기: %d GiB
                - 만료일: %s
                -------------------------------------
                
                궁금한 점이 있다면 관리자에게 문의해 주세요.
                """,
                user.getName(),
                request.getUbuntuUsername(),
                request.getResourceGroup().getServerName(),
                request.getContainerImage().getImageName(),
                request.getContainerImage().getImageVersion(),
                request.getVolumeSizeGiB(),
                request.getExpiresAt().toLocalDate().toString()
        );

        sendAllAlerts(user.getName(), user.getEmail(), subject, message);
    }

}