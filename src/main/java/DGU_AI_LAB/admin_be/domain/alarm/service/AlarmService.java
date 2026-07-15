package DGU_AI_LAB.admin_be.domain.alarm.service;

import DGU_AI_LAB.admin_be.domain.alarm.dto.SlackMessageDto;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import DGU_AI_LAB.admin_be.domain.pod.entity.PodExternalPort;
import DGU_AI_LAB.admin_be.domain.pod.repository.PodExternalPortRepository;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.global.util.MessageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * [알림 통합 관리 서비스]
 * 시스템 내 모든 알림(Slack, Email) 발송 요청의 진입점 역할을 합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AlarmService {

    /**
     * 모든 클래스에서 알림에 들어갈 메시지는 MessageUtil에서 관리하고 있어요.
     * 알림 문구를 수정하려면, resources/messages.properties에서 수정해주세요.
     */

    @Value("${slack-webhook-url.noti}") // 사용자 알림 로그용
    private String notiLogWebhookUrl;

    @Value("${slack-webhook-url.error-log}")
    private String errorLogWebhookUrl; // 중요한 에러 로그용

    // 관리자 승인 채널 (farm & lab)
    @Value("${slack-webhook-url.farm-admin}")
    private String farmAdminWebhookUrl;
    @Value("${slack-webhook-url.lab-admin}")
    private String labAdminWebhookUrl;

    @Value("${spring.mail.username}")
    private String from;

    private final JavaMailSender mailSender;
    private final SlackApiService slackApiService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final MessageUtils messageUtils;
    private final PodExternalPortRepository podExternalPortRepository;

    private static final String SLACK_QUEUE_KEY = "slack:notification:queue";

    // --- Public Methods ---
    public void sendSlackAlert(String message, String webhookUrl) {
        String urlToUse = (webhookUrl != null && !webhookUrl.isEmpty()) ? webhookUrl : errorLogWebhookUrl;

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
            sendSlackAlert("🚨 메일 전송 실패! 수신자: " + to, errorLogWebhookUrl);
        }
    }

    /**
     * [사용자 알림 + 관리자 로그]
     * 사용자에게는 실제 알림을, 관리자 'noti' 채널에는 로그를 남깁니다.
     */
    public void sendAllAlerts(String username, String email, String subject, String message) {
        // 1. 사용자 발송
        sendMailAlert(email, subject, message);
        sendDMAlert(username, email, message);

        // 2. 관리자 로그 채널(noti)에 기록
        sendMonitoringLog(username, email, subject);
    }

    // --- Helper / Formatting Methods ---
    /**
     * noti 채널에 짧은 로그(영수증)를 남기는 메서드
     */
    private void sendMonitoringLog(String username, String email, String subject) {
        try {
            // properties: notification.monitor.log
            String logMessage = messageUtils.get("notification.monitor.log", username, email, subject);

            // 명시적으로 'noti' 채널 URL 사용
            sendSlackAlert(logMessage, notiLogWebhookUrl);
        } catch (Exception e) {
            log.warn("로그 전송 실패", e);
        }
    }

    private String getAdminWebhookUrl(String serverName) {
        if ("FARM".equalsIgnoreCase(serverName)) return farmAdminWebhookUrl;
        else if ("LAB".equalsIgnoreCase(serverName)) return labAdminWebhookUrl;
            // 알 수 없는 서버라면 에러 채널로 보내서 관리자가 확인하게 합니다.
        else return errorLogWebhookUrl;
    }

    public void sendNewRequestNotification(Request request) {
        String serverName = request.getResourceGroup().getServerName();
        String message = messageUtils.get("notification.admin.new-request",
                request.getUser().getName(), serverName);

        sendSlackAlert(message, getAdminWebhookUrl(serverName));
    }

    /**
     * [컨테이너 배정 안내 메일] 신청 승인 시 접속 정보를 담아 사용자에게 발송.
     * 본문에 초기 비밀번호가 들어가므로 Slack DM은 보내지 않고(이메일 전용),
     * 관리자 noti 채널엔 본문 없는 수신 로그만 남긴다.
     * 포트는 문자열로 받는다 — MessageFormat에 숫자형을 주면 30,888처럼 콤마가 붙는다.
     */
    public void sendContainerCreatedEmail(Request request, String sshPort, String jupyterPort) {
        User user = request.getUser();
        var image = request.getContainerImage();
        String serverName = request.getResourceGroup().getServerName();

        // FARM은 pfSense가 210.94.179.19:9300-9397 → farm6:30000-30097로 오프셋 포워딩하므로
        // 사용자에겐 NodePort가 아니라 공인 포트를 안내해야 한다 (26-07-15 외부 실측 검증).
        if ("FARM".equalsIgnoreCase(serverName)) {
            sshPort = toFarmPublicPort(sshPort);
            jupyterPort = toFarmPublicPort(jupyterPort);
        }

        String subject = messageUtils.get("email.container.created.subject", serverName);
        String body = messageUtils.get("email.container.created.body",
                user.getName(),                                        // {0}
                image.getImageName() + ":" + image.getImageVersion(),  // {1}
                request.getUbuntuUsername(),                           // {2}
                sshPort,                                               // {3}
                jupyterPort,                                           // {4}
                resolveHostIp(serverName),                             // {5}
                request.getUbuntuPassword());                          // {6}

        sendMailAlert(user.getEmail(), subject, body);
        sendMonitoringLog(user.getName(), user.getEmail(), subject);
    }

    // ponytail: pfSense FARM 오프셋 매핑 하드코딩(admin_fe publicEndpoint.js와 동일 공식). 대역이 늘면 설정으로.
    private static final int NODEPORT_BASE = 30000;
    private static final int FARM_PUBLIC_PORT_BASE = 9300;
    private static final int FARM_PUBLIC_BAND_SIZE = 98; // 외부 9300-9397

    private String toFarmPublicPort(String nodePort) {
        try {
            int offset = Integer.parseInt(nodePort) - NODEPORT_BASE;
            if (offset >= 0 && offset < FARM_PUBLIC_BAND_SIZE) {
                return String.valueOf(FARM_PUBLIC_PORT_BASE + offset);
            }
        } catch (NumberFormatException ignored) {
            // 빈 문자열 등 — 원본 그대로 반환
        }
        return nodePort; // 매핑 대역 밖이면 원본 유지(외부 미개방 포트임이 그대로 드러나는 편이 낫다)
    }

    // ponytail: LAB/FARM 2-호스트 하드코딩. 호스트가 늘면 노드/설정 레지스트리로.
    private String resolveHostIp(String serverName) {
        if (serverName == null) return "";
        return switch (serverName.toUpperCase()) {
            case "LAB"  -> "210.94.179.18";
            case "FARM" -> "210.94.179.19";
            default -> { log.warn("호스트 IP 미상 serverName={}", serverName); yield ""; }
        };
    }

    /**
     * [관리자 수동 삭제 안내 메일] 관리자가 계정 삭제 시 사용자에게 발송.
     */
    public void sendContainerDeletedEmail(Request request) {
        User user = request.getUser();
        String serverName = request.getResourceGroup().getServerName();
        List<PodExternalPort> ports = podExternalPortRepository.findByRequestRequestId(request.getRequestId());

        String subject = messageUtils.get("email.container.deleted.subject", serverName);
        String body = messageUtils.get("email.container.deleted.body",
                user.getName(),                              // {0}
                serverName,                                  // {1}
                request.getUbuntuUsername(),                 // {2}
                request.getPodName(),                        // {3}
                formatPortSummary(ports),                    // {4}
                LocalDate.now().toString());                 // {5}

        sendMailAlert(user.getEmail(), subject, body);
        sendMonitoringLog(user.getName(), user.getEmail(), subject);
    }

    /**
     * [만료일 연장 승인 안내 메일] 관리자가 EXPIRES_AT 변경 요청 승인 시 사용자에게 발송.
     */
    public void sendContainerExtendedEmail(Request request, LocalDateTime oldExpiresAt, LocalDateTime newExpiresAt) {
        User user = request.getUser();
        String serverName = request.getResourceGroup().getServerName();
        List<PodExternalPort> ports = podExternalPortRepository.findByRequestRequestId(request.getRequestId());

        String subject = messageUtils.get("email.container.extended.subject", serverName);
        String body = messageUtils.get("email.container.extended.body",
                user.getName(),                              // {0}
                serverName,                                  // {1}
                request.getUbuntuUsername(),                 // {2}
                newExpiresAt.toLocalDate().toString(),       // {3}
                oldExpiresAt.toLocalDate().toString(),       // {4}
                request.getPodName(),                        // {5}
                formatPortSummary(ports));                   // {6}

        sendMailAlert(user.getEmail(), subject, body);
        sendMonitoringLog(user.getName(), user.getEmail(), subject);
    }

    private String formatPortSummary(List<PodExternalPort> ports) {
        if (ports == null || ports.isEmpty()) return "없음";
        return ports.stream()
                .map(p -> p.getUsagePurpose() + "(" + p.getExternalPort() + ")")
                .collect(Collectors.joining(", "));
    }

    public void sendAdminSlackNotification(String serverName, String message) {
        sendSlackAlert(message, getAdminWebhookUrl(serverName));
    }

    // --- Fallback Logic ---
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
        String notice = messageUtils.get("notification.error.redis-fallback");
        String fullMessage = dto.getMessage() + notice;

        try {
            if (dto.getType() == SlackMessageDto.MessageType.WEBHOOK) {
                slackApiService.sendWebhook(dto.getWebhookUrl(), fullMessage);
            } else {
                slackApiService.sendDM(dto.getUsername(), dto.getEmail(), fullMessage);
            }

            // Fallback이 작동했다는 건 시스템이 불안정하다는 뜻이므로 에러 로그 채널에 알립니다.
            String webhookUrl = dto.getWebhookUrl();
            if (webhookUrl == null || !webhookUrl.equals(errorLogWebhookUrl)) {
                slackApiService.sendWebhook(errorLogWebhookUrl, "⚠️ Redis 장애 발생 (Direct Send 작동됨)");
            }

            log.info("✅ Fallback 직접 전송 성공");
        } catch (Exception ex) {
            log.error("❌ Fallback 실패", ex);
        }
    }
}