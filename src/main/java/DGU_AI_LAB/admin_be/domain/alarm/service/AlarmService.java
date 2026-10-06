package DGU_AI_LAB.admin_be.domain.alarm.service;

import DGU_AI_LAB.admin_be.domain.alarm.SlackText;
import DGU_AI_LAB.admin_be.domain.alarm.dto.SlackMessageDto;
import DGU_AI_LAB.admin_be.domain.pod.PodPortUtils;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import DGU_AI_LAB.admin_be.domain.pod.entity.PodExternalPort;
import DGU_AI_LAB.admin_be.domain.pod.repository.PodExternalPortRepository;
import DGU_AI_LAB.admin_be.domain.portRequests.entity.PortRequests;
import DGU_AI_LAB.admin_be.domain.requests.entity.ChangeRequest;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.global.server.ServerProfileRegistry;
import DGU_AI_LAB.admin_be.global.util.MessageUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    /**
     * 테스트용 계정의 이메일 도메인 앞부분(예: tester@e2e.local). 이런 계정의 새 신청서는 서버별 신청서 채널 대신
     * 알림 기록(noti) 채널로 간다 — 신청서 채널은 승인자가 실제 신청을 판단하는 곳이라 시험 신청이 섞이면 안 된다.
     * 가입은 학교 도메인만 받으므로(EmailDomainPolicy) 이 도메인 계정은 점검 도구가 DB에 직접 넣은 것뿐이다.
     */
    private static final String TEST_EMAIL_DOMAIN_PREFIX = "e2e";

    /** 신청 화면이 폼 응답에 팀 프로젝트 정보를 담는 키. */
    private static final String FORM_ANSWER_TEAM_INFO = "teamInfo";
    /** 신청서 알림에 적는 접수 시각 형식(년-월-일 시:분). */
    private static final DateTimeFormatter RECEIVED_AT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final ObjectMapper FORM_ANSWERS_MAPPER = new ObjectMapper();

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
     * 신청자 신원·연락처·신청 자원·사용 목적·기간까지 한 메시지 안에 다 있어야 한다.
     * {0}~{20}의 뜻은 예전 양식과 같게 둔다 — 관리자가 DB(message_templates)에 고쳐 둔 양식도 그대로 쓰이게 하기 위해서다.
     * 숫자는 문자열로 넘긴다 — MessageFormat에 숫자형을 주면 1,234처럼 콤마가 붙는다.
     */
    public void sendNewRequestNotification(Request request, List<PortRequests> portRequests, long activeContainerCount) {
        User user = request.getUser();
        var resourceGroup = request.getResourceGroup();
        var image = request.getContainerImage();
        String serverName = resourceGroup.getServerName();
        LocalDate appliedOn = request.getCreatedAt() != null
                ? request.getCreatedAt().toLocalDate()
                : LocalDate.now(ZoneId.of("Asia/Seoul"));
        LocalDateTime receivedAt = request.getCreatedAt() != null
                ? request.getCreatedAt()
                : LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        LocalDate expiresOn = request.getExpiresAt().toLocalDate();

        String message = messageUtils.get("notification.admin.new-request",
                SlackText.escape(user.getName()),                          // {0}
                SlackText.escape(user.getStudentId()),                     // {1}
                SlackText.escape(user.getDepartment()),                    // {2}
                SlackText.escape(user.getEmail()),                         // {3}
                SlackText.escape(user.getPhone()),                         // {4}
                request.getUbuntuUsername(),                               // {5}
                SlackText.escape(resourceGroup.getResourceGroupName()),    // {6}
                serverName,                                                // {7}
                SlackText.escape(request.getUsagePurpose()),               // {8}
                expiresOn.toString(),                                      // {9}
                String.valueOf(request.getRequestId()),                    // {10}
                describe(resourceGroup.getDescription()),                  // {11}
                image == null ? "-" : SlackText.escape(image.getImageName() + ":" + image.getImageVersion()), // {12}
                formatGroups(request),                                     // {13}
                formatPortRequests(portRequests),                          // {14}
                request.isEnableVnc() ? "사용" : "사용 안 함",               // {15}
                appliedOn.toString(),                                      // {16}
                String.valueOf(ChronoUnit.DAYS.between(appliedOn, expiresOn)), // {17}
                String.valueOf(activeContainerCount),                      // {18}
                formatTeamInfo(request),                                   // {19}
                RECEIVED_AT_FORMAT.format(receivedAt));                    // {20}

        sendSlackAlert(message, requestChannelUrl(user, serverName));
    }

    /**
     * 승인 전에 신청자가 취소한 신청을 신청서 채널에 알린다. 신청서는 그 채널에 그대로 남아 있어, 알리지 않으면
     * 승인자가 이미 없는 신청을 처리하려 한다.
     *
     * <p>신청 정보는 호출한 시점(트랜잭션 안)에 읽어 문구를 만들고, 전송은 돌려준 작업을 실행할 때 한다 — 호출자가
     * 커밋 뒤에 실행한다(Redis 장애 시 직접 전송으로 폴백하므로 행 잠금을 쥔 채 보내지 않는다).
     */
    public Runnable prepareRequestCancelledNotification(Request request) {
        User user = request.getUser();
        var resourceGroup = request.getResourceGroup();
        String serverName = resourceGroup.getServerName();
        LocalDateTime receivedAt = request.getCreatedAt() != null
                ? request.getCreatedAt()
                : LocalDateTime.now(ZoneId.of("Asia/Seoul"));

        String message = messageUtils.get("notification.admin.request-cancelled",
                serverName,                                                // {0}
                RECEIVED_AT_FORMAT.format(receivedAt),                     // {1}
                SlackText.escape(user.getName()));                         // {2}
        String webhookUrl = requestChannelUrl(user, serverName);
        return () -> sendSlackAlert(message, webhookUrl);
    }

    /**
     * 신청서와 그 신청의 취소 알림이 가는 채널. 서버별 신청서 채널로 가고(따로 없으면 관리 채널), 그 채널에는
     * 이 둘 말고 다른 알림을 보내지 않는다. 테스트용 계정의 것만 알림 기록 채널로 돌린다.
     */
    private String requestChannelUrl(User user, String serverName) {
        return isTestAccount(user.getEmail())
                ? notiLogWebhookUrl
                : serverProfileRegistry.requestWebhookUrl(serverName).orElse(errorLogWebhookUrl);
    }

    static boolean isTestAccount(String email) {
        if (email == null) {
            return false;
        }
        int at = email.lastIndexOf('@');
        return at >= 0 && email.substring(at + 1).toLowerCase(Locale.ROOT).startsWith(TEST_EMAIL_DOMAIN_PREFIX);
    }

    private static String describe(String description) {
        return description == null || description.isBlank() ? "" : " (" + SlackText.escape(description) + ")";
    }

    private static String formatGroups(Request request) {
        // 승인 대기 그룹은 이 신청을 승인할 때 인프라에 새로 만들어진다 — 승인자가 알 수 있게 표시한다.
        String groups = request.getRequestGroups().stream()
                .map(rg -> rg.getGroup().isPending()
                        ? rg.getGroup().getGroupName() + " (새 그룹 - 승인 시 생성)"
                        : rg.getGroup().getGroupName())
                .sorted()
                .collect(Collectors.joining(", "));
        return groups.isEmpty() ? "없음" : SlackText.escape(groups);
    }

    /**
     * 신청서 폼 응답(JSON)에서 팀 프로젝트 정보(그룹 이름·팀원 이름)를 꺼낸다.
     * 승인자는 팀원이 모두 신청했는지 이 글로 확인한다. 적지 않았거나 읽을 수 없으면 "없음"이다.
     */
    private static String formatTeamInfo(Request request) {
        String formAnswers = request.getFormAnswers();
        if (formAnswers == null || formAnswers.isBlank()) {
            return "없음";
        }
        try {
            String teamInfo = FORM_ANSWERS_MAPPER.readTree(formAnswers).path(FORM_ANSWER_TEAM_INFO).asText("");
            return teamInfo.isBlank() ? "없음" : SlackText.escape(teamInfo.strip());
        } catch (JsonProcessingException e) {
            return "없음";
        }
    }

    private static String formatPortRequests(List<PortRequests> portRequests) {
        if (portRequests == null || portRequests.isEmpty()) {
            return "없음";
        }
        return portRequests.stream()
                .map(p -> p.getInternalPort() + "번 (" + SlackText.escape(p.getUsagePurpose()) + ")")
                .collect(Collectors.joining(", "));
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

    /**
     * [접속 포트 변경 안내 메일] 마이그레이션으로 새 Pod에 다른 포트가 배정됐을 때 보낸다. 사용자가 저장해 둔
     * SSH 설정이 조용히 끊기지 않게 생성 안내와 같은 방식(포워딩 서버는 공인 포트)으로 새 접속 정보를 알린다.
     * 포트는 커밋된 신청의 포트 기록에서 읽는다.
     */
    public void sendContainerPortsChangedEmail(Request request) {
        User user = request.getUser();
        String serverName = request.getResourceGroup().getServerName();
        List<PodExternalPort> allPorts = podExternalPortRepository.findByRequestRequestId(request.getRequestId());

        String sshPort = serverProfileRegistry.publicPort(serverName, externalPortOf(allPorts, "ssh"));
        String jupyterPort = serverProfileRegistry.publicPort(serverName, externalPortOf(allPorts, "jupyter"));

        String subject = messageUtils.get("email.container.ports-changed.subject", serverName);
        String body = messageUtils.get("email.container.ports-changed.body",
                user.getName(),                                   // {0}
                request.getUbuntuUsername(),                      // {1}
                sshPort,                                          // {2}
                jupyterPort,                                      // {3}
                resolveHostIp(serverName),                        // {4}
                PodPortUtils.formatExtraPortSummary(allPorts));   // {5}

        sendMailAlert(user.getEmail(), subject, body);
        sendMonitoringLog(user.getName(), user.getEmail(), subject);
    }

    private static String externalPortOf(List<PodExternalPort> ports, String purpose) {
        return ports.stream()
                .filter(p -> purpose.equalsIgnoreCase(p.getUsagePurpose()))
                .map(p -> String.valueOf(p.getExternalPort()))
                .findFirst()
                .orElse("");
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

    /**
     * 신청자에게 접수됐음을 알린다. 접수 여부를 알 길이 없으면 같은 신청을 다시 낸다.
     * 관리자 쪽에는 같은 신청이 신청서 채널로 이미 가므로 알림 기록은 따로 남기지 않는다.
     */
    public void sendRequestReceivedEmail(Request request) {
        User user = request.getUser();
        var resourceGroup = request.getResourceGroup();
        var image = request.getContainerImage();
        String serverName = resourceGroup.getServerName();
        LocalDateTime receivedAt = request.getCreatedAt() != null
                ? request.getCreatedAt()
                : LocalDateTime.now(ZoneId.of("Asia/Seoul"));

        String subject = messageUtils.get("email.request.received.subject", serverName);
        String body = messageUtils.get("email.request.received.body",
                user.getName(),                                            // {0}
                String.valueOf(request.getRequestId()),                    // {1}
                serverName,                                                // {2}
                resourceGroup.getResourceGroupName(),                      // {3}
                image == null ? "-" : image.getImageName() + ":" + image.getImageVersion(), // {4}
                request.getExpiresAt().toLocalDate().toString(),           // {5}
                RECEIVED_AT_FORMAT.format(receivedAt));                    // {6}

        sendMailAlert(user.getEmail(), subject, body);
    }

    public void sendRequestRejectedEmail(Request request, String adminComment) {
        User user = request.getUser();
        String serverName = request.getResourceGroup().getServerName();

        String subject = messageUtils.get("email.request.rejected.subject", serverName);
        String body = messageUtils.get("email.request.rejected.body",
                user.getName(),    // {0}
                serverName,        // {1}
                orNone(adminComment)); // {2}

        sendMailAlert(user.getEmail(), subject, body);
        sendMonitoringLog(user.getName(), user.getEmail(), subject);
    }

    public void sendModificationRejectedEmail(ChangeRequest changeRequest, String adminComment) {
        User user = changeRequest.getRequestedBy();
        String changeType = changeRequest.getChangeType().label();

        String subject = messageUtils.get("email.modification.rejected.subject", changeType);
        String body = messageUtils.get("email.modification.rejected.body",
                user.getName(),    // {0}
                changeType,        // {1}
                orNone(adminComment)); // {2}

        sendMailAlert(user.getEmail(), subject, body);
        sendMonitoringLog(user.getName(), user.getEmail(), subject);
    }

    /**
     * [변경 요청 승인 안내 메일] EXPIRES_AT 외 타입(GROUP/RESOURCE_GROUP/CONTAINER_IMAGE/PORT) 공통.
     * EXPIRES_AT은 sendContainerExtendedEmail로 별도의 상세 메일을 보낸다.
     */
    public void sendModificationApprovedEmail(ChangeRequest changeRequest, String adminComment) {
        User user = changeRequest.getRequestedBy();
        String changeType = changeRequest.getChangeType().label();

        String subject = messageUtils.get("email.modification.approved.subject", changeType);
        String body = messageUtils.get("email.modification.approved.body",
                user.getName(),    // {0}
                changeType,        // {1}
                orNone(adminComment)); // {2}

        sendMailAlert(user.getEmail(), subject, body);
        sendMonitoringLog(user.getName(), user.getEmail(), subject);
    }

    /**
     * 그룹 추가 승인 안내. 승인 메일만 받은 사용자도 팀과 파일을 나눌 방법을 바로 알도록 그룹마다 홈 아래 폴더를
     * 그 그룹과 공유하는 명령을 적는다. 명령 형식은 이미지 구성에 달려 있어 messages.properties의 한 줄 양식으로 둔다.
     */
    public void sendGroupAddedEmail(ChangeRequest changeRequest, String adminComment, List<String> groupNames) {
        User user = changeRequest.getRequestedBy();
        String changeType = changeRequest.getChangeType().label();
        String shareCommands = groupNames.stream()
                .map(name -> messageUtils.get("email.modification.approved.group.dir", name))
                .collect(Collectors.joining("\n"));

        String subject = messageUtils.get("email.modification.approved.subject", changeType);
        String body = messageUtils.get("email.modification.approved.group.body",
                user.getName(),    // {0}
                changeType,        // {1}
                orNone(adminComment), // {2}
                shareCommands);    // {3}

        sendMailAlert(user.getEmail(), subject, body);
        sendMonitoringLog(user.getName(), user.getEmail(), subject);
    }

    /** 관리자가 메모를 비워 두면 MessageFormat이 "null"을 찍으므로 "없음"으로 바꾼다. */
    private static String orNone(String text) {
        return text == null || text.isBlank() ? "없음" : text;
    }

    /** 관리자가 나중에 되짚어 볼 처리 기록을 알림 기록(noti) 채널에 남긴다. 승인 판단용 채널에는 보내지 않는다. */
    public void sendNotiLog(String message) {
        sendSlackAlert(message, notiLogWebhookUrl);
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