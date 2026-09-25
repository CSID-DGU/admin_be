package DGU_AI_LAB.admin_be.domain.alarm.service;

import DGU_AI_LAB.admin_be.domain.alarm.dto.SlackMessageDto;
import DGU_AI_LAB.admin_be.domain.pod.PodPortUtils;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import DGU_AI_LAB.admin_be.domain.pod.entity.PodExternalPort;
import DGU_AI_LAB.admin_be.domain.pod.repository.PodExternalPortRepository;
import DGU_AI_LAB.admin_be.domain.requests.entity.ChangeRequest;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.global.server.ServerProfileRegistry;
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

    @Value("${spring.mail.username}")
    private String from;

    private final JavaMailSender mailSender;
    private final SlackApiService slackApiService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final MessageUtils messageUtils;
    private final PodExternalPortRepository podExternalPortRepository;
    private final ServerProfileRegistry serverProfileRegistry;

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
            log.error("메일 전송 실패: 수신자={}", maskEmail(to), e);
            sendSlackAlert("🚨 메일 전송 실패! 수신자: " + maskEmail(to), errorLogWebhookUrl);
        }
    }

    private static String maskEmail(String email) {
        if (email == null) {
            return "unknown";
        }
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
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

    // 알 수 없는 서버라면 에러 채널로 보내서 관리자가 확인하게 한다.
    private String getAdminWebhookUrl(String serverName) {
        return serverProfileRegistry.adminWebhookUrl(serverName).orElse(errorLogWebhookUrl);
    }

    /**
     * 신청이 들어오면 서버별 관리 교수님 채널에 승인 판단에 필요한 정보를 전부 담아 보낸다.
     * 이 채널에서 교수님이 직접 보고 승인 여부를 판단하므로(관리자 페이지를 거치지 않을 수 있다),
     * 신청자 신원·연락처·사용 목적·희망 기간까지 한 메시지 안에 다 있어야 한다.
     */
    public void sendNewRequestNotification(Request request) {
        User user = request.getUser();
        var resourceGroup = request.getResourceGroup();
        String serverName = resourceGroup.getServerName();
        String message = messageUtils.get("notification.admin.new-request",
                user.getName(), user.getStudentId(), user.getDepartment(), user.getEmail(), user.getPhone(),
                request.getUbuntuUsername(), resourceGroup.getResourceGroupName(), serverName,
                request.getUsagePurpose(), request.getExpiresAt().toLocalDate());

        sendSlackAlert(message, getAdminWebhookUrl(serverName));
    }

    /**
     * [컨테이너 배정 안내 메일] 신청 승인 시 접속 정보를 담아 사용자에게 발송.
     * 비밀번호는 저장하지 않으므로(해시만 보관) 본문에는 "신청 때 입력한 비밀번호"라는 안내만 넣는다.
     * {6} 자리를 유지하는 건 관리자가 DB(message_templates)에 고쳐 둔 본문도 그대로 쓰이게 하기 위해서다.
     * Slack DM은 보내지 않고(이메일 전용), 관리자 noti 채널엔 본문 없는 수신 로그만 남긴다.
     * 포트는 문자열로 받는다 — MessageFormat에 숫자형을 주면 30,888처럼 콤마가 붙는다.
     */
    public void sendContainerCreatedEmail(Request request, String sshPort, String jupyterPort) {
        User user = request.getUser();
        var image = request.getContainerImage();
        String serverName = request.getResourceGroup().getServerName();

        // 공인 주소가 NodePort를 다른 포트 대역으로 포워딩하는 서버면 사용자에겐 공인 포트를 안내해야 한다.
        sshPort = serverProfileRegistry.publicPort(serverName, sshPort);
        jupyterPort = serverProfileRegistry.publicPort(serverName, jupyterPort);

        List<PodExternalPort> allPorts = podExternalPortRepository.findByRequestRequestId(request.getRequestId());
        String extraPorts = PodPortUtils.formatExtraPortSummary(allPorts);

        String subject = messageUtils.get("email.container.created.subject", serverName);
        String body = messageUtils.get("email.container.created.body",
                user.getName(),                                        // {0}
                image.getImageName() + ":" + image.getImageVersion(),  // {1}
                request.getUbuntuUsername(),                           // {2}
                sshPort,                                               // {3}
                jupyterPort,                                           // {4}
                resolveHostIp(serverName),                             // {5}
                messageUtils.get("email.container.created.password-notice"), // {6}
                extraPorts);                                           // {7}

        sendMailAlert(user.getEmail(), subject, body);
        sendMonitoringLog(user.getName(), user.getEmail(), subject);
    }

    private String resolveHostIp(String serverName) {
        return serverProfileRegistry.publicHost(serverName).orElseGet(() -> {
            log.warn("호스트 주소 미상 serverName={}", serverName);
            return "";
        });
    }

    /**
     * [관리자 수동 삭제 안내 메일] 관리자가 계정 삭제 시 사용자에게 발송.
     */
    public void sendContainerDeletedEmail(Request request) {
        List<PodExternalPort> ports = podExternalPortRepository.findByRequestRequestId(request.getRequestId());
        sendContainerDeletedEmail(request, ports);
    }

    public void sendContainerDeletedEmail(Request request, List<PodExternalPort> ports) {
        User user = request.getUser();
        String serverName = request.getResourceGroup().getServerName();
        String podName = request.getPodName() != null ? request.getPodName() : "미배정";

        String subject = messageUtils.get("email.container.deleted.subject", serverName);
        String body = messageUtils.get("email.container.deleted.body",
                user.getName(),                              // {0}
                serverName,                                  // {1}
                request.getUbuntuUsername(),                 // {2}
                podName,                                     // {3}
                PodPortUtils.formatPortSummary(ports),       // {4}
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
        String oldDate = oldExpiresAt != null ? oldExpiresAt.toLocalDate().toString() : "이전 기록 없음";
        String podName = request.getPodName() != null ? request.getPodName() : "미배정";

        String subject = messageUtils.get("email.container.extended.subject", serverName);
        String body = messageUtils.get("email.container.extended.body",
                user.getName(),                              // {0}
                serverName,                                  // {1}
                request.getUbuntuUsername(),                 // {2}
                newExpiresAt.toLocalDate().toString(),       // {3}
                oldDate,                                     // {4}
                podName,                                     // {5}
                PodPortUtils.formatPortSummary(ports));      // {6}

        sendMailAlert(user.getEmail(), subject, body);
        sendMonitoringLog(user.getName(), user.getEmail(), subject);
    }

    public void sendRequestRejectedEmail(Request request, String adminComment) {
        User user = request.getUser();
        String serverName = request.getResourceGroup().getServerName();

        String subject = messageUtils.get("email.request.rejected.subject", serverName);
        String body = messageUtils.get("email.request.rejected.body",
                user.getName(),    // {0}
                serverName,        // {1}
                adminComment);     // {2}

        sendMailAlert(user.getEmail(), subject, body);
        sendMonitoringLog(user.getName(), user.getEmail(), subject);
    }

    public void sendModificationRejectedEmail(ChangeRequest changeRequest, String adminComment) {
        User user = changeRequest.getRequestedBy();
        String changeType = changeRequest.getChangeType().name();

        String subject = messageUtils.get("email.modification.rejected.subject", changeType);
        String body = messageUtils.get("email.modification.rejected.body",
                user.getName(),    // {0}
                changeType,        // {1}
                adminComment);     // {2}

        sendMailAlert(user.getEmail(), subject, body);
        sendMonitoringLog(user.getName(), user.getEmail(), subject);
    }

    /**
     * [변경 요청 승인 안내 메일] EXPIRES_AT 외 타입(GROUP/RESOURCE_GROUP/CONTAINER_IMAGE/PORT) 공통.
     * EXPIRES_AT은 sendContainerExtendedEmail로 별도의 상세 메일을 보낸다.
     */
    public void sendModificationApprovedEmail(ChangeRequest changeRequest, String adminComment) {
        User user = changeRequest.getRequestedBy();
        String changeType = changeRequest.getChangeType().name();

        String subject = messageUtils.get("email.modification.approved.subject", changeType);
        String body = messageUtils.get("email.modification.approved.body",
                user.getName(),    // {0}
                changeType,        // {1}
                adminComment);     // {2}

        sendMailAlert(user.getEmail(), subject, body);
        sendMonitoringLog(user.getName(), user.getEmail(), subject);
    }

    /**
     * 그룹 추가 승인 안내. 승인 메일만 받은 사용자도 팀과 파일을 나눌 자리를 바로 찾도록 그룹마다 팀 디렉터리
     * 경로를 적는다. 경로 형식은 이미지·스토리지 구성에 달려 있어 messages.properties의 한 줄 양식으로 둔다.
     */
    public void sendGroupAddedEmail(ChangeRequest changeRequest, String adminComment, List<String> groupNames) {
        User user = changeRequest.getRequestedBy();
        String changeType = changeRequest.getChangeType().name();
        String teamDirs = groupNames.stream()
                .map(name -> messageUtils.get("email.modification.approved.group.dir", name))
                .collect(Collectors.joining("\n"));

        String subject = messageUtils.get("email.modification.approved.subject", changeType);
        String body = messageUtils.get("email.modification.approved.group.body",
                user.getName(),    // {0}
                changeType,        // {1}
                adminComment,      // {2}
                teamDirs);         // {3}

        sendMailAlert(user.getEmail(), subject, body);
        sendMonitoringLog(user.getName(), user.getEmail(), subject);
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