package DGU_AI_LAB.admin_be.domain.alarm.service;

import DGU_AI_LAB.admin_be.domain.alarm.dto.SlackMessageDto;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AlarmService {

    @Value("${slack-webhook-url.monitoring}")
    private String defaultWebhookUrl;
    @Value("${slack-webhook-url.farm-admin}")
    private String farmAdminWebhookUrl;
    @Value("${slack-webhook-url.lab-admin}")
    private String labAdminWebhookUrl;
    @Value("${spring.mail.username}")
    private String from;

    private final JavaMailSender mailSender;
    private final SlackApiService slackApiService;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String SLACK_QUEUE_KEY = "slack:notification:queue";

    // --- Public Methods ---

    public void sendSlackAlert(String message, String webhookUrl) {
        String urlToUse = (webhookUrl != null && !webhookUrl.isEmpty()) ? webhookUrl : defaultWebhookUrl;
        SlackMessageDto dto = SlackMessageDto.builder()
                .type(SlackMessageDto.MessageType.WEBHOOK)
                .webhookUrl(urlToUse)
                .message(message)
                .build();
        pushToQueue(dto);
    }

    public void sendDMAlert(String username, String email, String message) {
        SlackMessageDto dto = SlackMessageDto.builder()
                .type(SlackMessageDto.MessageType.DM)
                .username(username)
                .email(email)
                .message(message)
                .build();
        pushToQueue(dto);
    }

    public void sendMailAlert(String to, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
        } catch (Exception e) {
            log.error("메일 전송 실패: 수신자={}", to, e);
        }
    }

    public void sendAllAlerts(String username, String email, String subject, String message) {
        sendMailAlert(email, subject, message);
        sendDMAlert(username, email, message);
    }

    // --- Helper / Formatting Methods ---

    private String getAdminWebhookUrl(String serverName) {
        if ("FARM".equalsIgnoreCase(serverName)) return farmAdminWebhookUrl;
        else if ("LAB".equalsIgnoreCase(serverName)) return labAdminWebhookUrl;
        else return defaultWebhookUrl;
    }

    public void sendNewRequestNotification(Request request) {
        String serverName = request.getResourceGroup().getServerName();
        String message = String.format(
                "🔔 새로운 서버 사용 신청! 🔔\n▶ 신청자: %s\n▶ 서버: %s\n(관리자 페이지 확인 요망)",
                request.getUser().getName(), serverName);
        sendSlackAlert(message, getAdminWebhookUrl(serverName));
    }

    public void sendApprovalNotification(Request request) {
        User user = request.getUser();
        String subject = "[DGU AI LAB] 서버 사용 신청 승인";
        String message = String.format("🎉 %s님의 신청이 승인되었습니다.", user.getName());
        sendAllAlerts(user.getName(), user.getEmail(), subject, message);
    }

    public void sendAdminSlackNotification(String serverName, String message) {
        sendSlackAlert(message, getAdminWebhookUrl(serverName));
    }

    // --- Private Queue Logic with Fallback ---

    private void pushToQueue(SlackMessageDto dto) {
        try {
            redisTemplate.opsForList().rightPush(SLACK_QUEUE_KEY, dto);
            log.debug("Slack 큐 적재: {}", dto.getType());
        } catch (Exception e) {
            log.error("⚠️ Redis 장애! 직접 전송 시도. ({})", e.getMessage());
            handleFallbackDirectSend(dto);
        }
    }

    private void handleFallbackDirectSend(SlackMessageDto dto) {
        String notice = "\n[⚠️ Redis 장애로 직접 발송됨]";
        String fullMessage = dto.getMessage() + notice;

        try {
            if (dto.getType() == SlackMessageDto.MessageType.WEBHOOK) {
                // 이제 SlackApiService를 사용하여 Fallback 처리 -> 코드 중복 제거됨
                slackApiService.sendWebhook(dto.getWebhookUrl(), fullMessage);
            } else {
                slackApiService.sendDM(dto.getUsername(), dto.getEmail(), fullMessage);
            }
            log.info("✅ Fallback 직접 전송 성공");
        } catch (Exception ex) {
            log.error("❌ Fallback 실패 (전송 불가)", ex);
        }
    }
}