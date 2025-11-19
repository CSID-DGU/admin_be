package DGU_AI_LAB.admin_be.domain.alarm.service;

import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * 사용자(이메일, DM) 및 관리자(Slack 채널)에게 알림을 전송하는 서비스
 */
@Service
@RequiredArgsConstructor
@Log4j2
public class AlarmService {

    // --- Slack Webhook (관리자 채널) ---
    @Value("${slack-webhook-url.monitoring}")
    private String defaultWebhookUrl;
    @Value("${slack-webhook-url.farm-admin}")
    private String farmAdminWebhookUrl;
    @Value("${slack-webhook-url.lab-admin}")
    private String labAdminWebhookUrl;

    // --- 외부 서비스 의존성 ---
    private final JavaMailSender mailSender;
    private final SlackApiService slackApiService;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${spring.mail.username}")
    private String from;

    /**
     * Slack Webhook을 사용하여 특정 채널에 메시지를 전송합니다.
     */
    public void sendSlackAlert(String message, String webhookUrl) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> payload = Map.of("text", message);
        HttpEntity<Map<String, String>> request = new HttpEntity<>(payload, headers);

        String urlToUse = (webhookUrl != null && !webhookUrl.isEmpty()) ? webhookUrl : defaultWebhookUrl;

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(urlToUse, request, String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                log.warn("Slack 알림 전송 실패: {}", response.getStatusCode());
            } else {
                log.debug("Slack 알림 전송 성공");
            }
        } catch (Exception e) {
            log.error("Slack 알림 전송 중 예외 발생: (URL: {})", urlToUse, e);
        }
    }

    /**
     * 사용자에게 이메일을 전송합니다.
     */
    public void sendMailAlert(String to, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            log.info("메일 전송 성공: 수신자={}, 제목={}", to, subject);
        } catch (Exception e) {
            log.error("메일 전송 실패: 수신자={}", to, e);
        }
    }

    /**
     * 사용자에게 Slack DM을 전송합니다.
     */
    public void sendDMAlert(String username, String email, String message) {
        slackApiService.sendDM(username, email, message);
    }

    /**
     * 사용자에게 DM과 메일을 모두 전송합니다. (주로 사용자 대상 알림)
     */
    public void sendAllAlerts(String username, String email, String subject, String message) {
        try {
            sendMailAlert(email, subject, message);
        } catch (Exception e) {
            log.error("sendAllAlerts 중 메일 전송 실패: {}", email, e);
        }

        try {
            sendDMAlert(username, email, message);
        } catch (Exception e) {
            log.error("sendAllAlerts 중 DM 전송 실패: {}", username, e);
        }
    }

    /**
     * serverName에 따라 적절한 Webhook URL을 반환합니다.
     */
    private String getAdminWebhookUrl(String serverName) {
        if ("FARM".equalsIgnoreCase(serverName)) {
            return farmAdminWebhookUrl;
        } else if ("LAB".equalsIgnoreCase(serverName)) {
            return labAdminWebhookUrl;
        } else {
            // FARM이나 LAB이 아닌 잘못된 입력값이 있을 경우, 기본 모니토링 채널로 전송
            log.warn("알 수 없는 serverName '{}'에 대한 요청 알림입니다. 기본 채널로 전송합니다.", serverName);
            return defaultWebhookUrl;
        }
    }

    /**
     * 관리자 채널(FARM/LAB)로 신규 신청 알림을 보냅니다.
     */
    public void sendNewRequestNotification(Request request) {
        String serverName = request.getResourceGroup().getServerName();
        String targetWebhookUrl = getAdminWebhookUrl(serverName); // 중복 로직 제거

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
     * 사용자에게 서버 사용 신청 승인 알림을 보냅니다. (DM + Email)
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

    /**
     * 서버 이름에 따라 적절한 관리자 채널로 메시지를 보냅니다.
     * @param serverName "FARM", "LAB" 등
     * @param message 보낼 메시지
     */
    public void sendAdminSlackNotification(String serverName, String message) {
        String targetWebhookUrl = getAdminWebhookUrl(serverName);
        sendSlackAlert(message, targetWebhookUrl);
    }
}