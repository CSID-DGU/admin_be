package DGU_AI_LAB.admin_be.domain.alarm.service;

import DGU_AI_LAB.admin_be.domain.alarm.SlackText;
import DGU_AI_LAB.admin_be.domain.alarm.dto.SlackMessageDto;
import DGU_AI_LAB.admin_be.domain.pod.PodPortUtils;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import DGU_AI_LAB.admin_be.domain.pod.entity.PodExternalPort;
import DGU_AI_LAB.admin_be.domain.pod.repository.PodExternalPortRepository;
import DGU_AI_LAB.admin_be.domain.portRequests.entity.PortRequests;
import DGU_AI_LAB.admin_be.domain.alarm.dto.ChangeRequestDecision;
import DGU_AI_LAB.admin_be.domain.alarm.dto.ChangeRequestNotice;
import DGU_AI_LAB.admin_be.domain.requests.entity.ChangeRequest;
import DGU_AI_LAB.admin_be.domain.requests.entity.ChangeType;
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
 *
 * <p>어디로 보낼지는 알림의 성격으로만 정한다. 호출부는 채널을 고르지 않고 아래 넷 중 하나를 부른다.
 * <ul>
 *   <li>결정 요청(승인·거절이 필요한 신청) → 신청서 채널: {@link #sendNewRequestNotification},
 *       그 신청의 후속 변화(취소·거절·생성 완료, 변경 요청의 승인 완료·거절)는 접수 알림 메시지의 스레드 댓글:
 *       {@link #prepareRequestCancelledNotification}, {@link #sendChangeRequestDecidedNotification}</li>
 *   <li>조치 필요(실패·멈춤·수동 확인) → 오류 채널 한 곳: {@link #alertNeedsAction}</li>
 *   <li>처리 기록(정상 완료) → 알림 기록(noti) 채널: {@link #recordLog}</li>
 *   <li>사용자 안내 → 메일 + Slack DM + 알림 기록 채널의 발송 기록: {@link #notifyUser}</li>
 * </ul>
 * 전송 실패는 여기서 잡아 기록한다 — 알림이 실패해도 호출한 쪽의 처리는 실패하지 않는다.
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

    /** 알림 기록 채널의 Slack 채널 ID(선택). 있으면 테스트용 계정의 신청서도 봇으로 올려 스레드 댓글을 단다. */
    @Value("${slack-channel-id.noti:}")
    private String notiChannelId;

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

    // --- 성격별 전송 창구 ---

    /**
     * 조치 필요: 실패했거나 멈췄거나 사람이 확인해야 하는 일. 오류 채널 한 곳으로만 보낸다.
     * 로그에는 어떤 알림인지(키)만 남긴다 — 값에는 이름·메일 주소가 들어올 수 있다.
     */
    public void alertNeedsAction(String messageKey, Object... args) {
        safely("조치 필요 알림", () -> {
            log.warn("[조치 필요] {}", messageKey);
            enqueueWebhook(render(messageKey, args), errorLogWebhookUrl);
        });
    }

    /** 처리 기록: 정상적으로 끝난 일을 나중에 되짚어 볼 수 있게 알림 기록(noti) 채널에 남긴다. */
    public void recordLog(String messageKey, Object... args) {
        safely("처리 기록 알림", () -> enqueueWebhook(render(messageKey, args), notiLogWebhookUrl));
    }

    /**
     * 사용자 안내: 메일과 Slack DM으로 보내고, 보냈다는 기록(본문 없음)을 알림 기록 채널에 남긴다.
     * 메일이 실패하면 기록 대신 오류 채널로 알린다. DM은 메일 성패와 상관없이 보낸다.
     */
    public void notifyUser(String username, String email, String subject, String body) {
        safely("사용자 안내", () -> {
            enqueueDM(username, email, body);
            mailWithReceipt(username, email, subject, body);
        });
    }

    /** 메일만 보내는 안내(접속 정보처럼 DM으로 보내지 않는 것). 발송 기록 규칙은 {@link #notifyUser}와 같다. */
    private void mailWithReceipt(String username, String email, String subject, String body) {
        if (sendMail(email, subject, body)) {
            enqueueWebhook(render("notification.monitor.log", username, email, subject), notiLogWebhookUrl);
        }
    }

    /**
     * Slack 채널로 보낼 문구 양식에 값을 채운다. 숫자는 문자열로 바꿔 넣는다 — MessageFormat에 숫자형을 주면 1,234처럼
     * 콤마가 붙는다. 글 값은 모두 Slack mrkdwn 이스케이프를 한다 — 이름·메일 주소·실패 사유처럼 사용자나 외부에서 온 글이
     * {@code <!channel>} 같은 채널 호출이나 링크로 바뀌지 않게, 호출부가 아니라 여기서 빠짐없이 막는다.
     * 양식은 DB(message_templates)를 먼저 찾으므로 DB 장애를 알리는 순간에는 읽지 못할 수 있다. 그때는 문구 대신
     * 키와 값을 그대로 보낸다 — 문구가 없다고 알림 자체를 잃으면 안 된다.
     */
    private String render(String messageKey, Object... args) {
        Object[] plain = Arrays.stream(args)
                .map(arg -> arg instanceof Number ? arg.toString() : arg)
                .map(arg -> arg instanceof String text ? SlackText.escapeKeepingEmpty(text) : arg)
                .toArray();
        try {
            return messageUtils.get(messageKey, plain);
        } catch (Exception e) {
            log.error("알림 문구를 읽지 못해 키와 값으로 대신 보냄: {}", messageKey, e);
            return messageKey + " " + Arrays.toString(plain);
        }
    }

    private void safely(String what, Runnable send) {
        try {
            send.run();
        } catch (Exception e) {
            log.error("{} 전송 실패", what, e);
        }
    }

    private void enqueueWebhook(String message, String webhookUrl) {
        enqueueWebhook(message, webhookUrl, false);
    }

    private void enqueueWebhook(String message, String webhookUrl, boolean blockLayout) {
        pushToQueue(SlackMessageDto.builder()
                .type(SlackMessageDto.MessageType.WEBHOOK)
                .webhookUrl(webhookUrl)
                .message(message)
                .blockLayout(blockLayout)
                .build());
    }

    private void enqueueDM(String username, String email, String message) {
        pushToQueue(SlackMessageDto.builder()
                .type(SlackMessageDto.MessageType.DM)
                .username(username)
                .email(email)
                .message(message)
                .build());
    }

    private boolean sendMail(String to, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            return true;
        } catch (Exception e) {
            log.error("메일 전송 실패: 수신자={}", maskEmail(to), e);
            alertNeedsAction("notification.admin.mail-failed", maskEmail(to));
            return false;
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

    // --- 결정 요청(신청서 채널) ---

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
                SlackText.line(user.getName()),                            // {0}
                SlackText.line(user.getStudentId()),                       // {1}
                SlackText.line(user.getDepartment()),                      // {2}
                SlackText.line(user.getEmail()),                           // {3}
                SlackText.line(user.getPhone()),                           // {4}
                request.getUbuntuUsername(),                               // {5}
                SlackText.line(resourceGroup.getResourceGroupName()),      // {6}
                serverName,                                                // {7}
                SlackText.quote(request.getUsagePurpose()),                // {8}
                expiresOn.toString(),                                      // {9}
                String.valueOf(request.getRequestId()),                    // {10}
                describe(resourceGroup.getDescription()),                  // {11}
                image == null ? "-" : SlackText.line(image.getImageName() + ":" + image.getImageVersion()), // {12}
                formatGroups(request),                                     // {13}
                formatPortRequests(portRequests),                          // {14}
                request.isEnableVnc() ? "사용" : "사용 안 함",               // {15}
                appliedOn.toString(),                                      // {16}
                String.valueOf(ChronoUnit.DAYS.between(appliedOn, expiresOn)), // {17}
                String.valueOf(activeContainerCount),                      // {18}
                formatTeamInfo(request),                                   // {19}
                RECEIVED_AT_FORMAT.format(receivedAt));                    // {20}

        enqueueRequestChannel(message, requestChannel(user, serverName), null, request.getRequestId(), null);
    }

    /**
     * 승인 전에 신청자가 취소한 신청을 신청서 채널에 알린다. 신청서는 그 채널에 그대로 남아 있어, 알리지 않으면
     * 승인자가 이미 없는 신청을 처리하려 한다. 신청서를 봇으로 올려 그 메시지의 식별자를 알고 있으면 스레드 댓글로 달고,
     * 모르면(webhook으로 보냈거나 아직 적히기 전) 일반 메시지로 보낸다.
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
                SlackText.line(user.getName()),                            // {2}
                String.valueOf(request.getRequestId()));                   // {3}
        RequestChannel channel = requestChannel(user, serverName);
        String threadTs = request.getSlackMessageTs();
        return () -> safely("신청 취소 알림", () -> replyToRequest(message, channel, threadTs));
    }

    /**
     * 거절한 신청을 신청서 채널에 알린다 — 그 신청서의 스레드 댓글로 달아(모르면 일반 메시지) 채널에 남은 신청서가
     * 처리되지 않은 것으로 보이지 않게 한다. 호출자가 커밋 뒤에 부른다.
     */
    public void sendRequestRejectedNotification(Request request, String adminComment) {
        safely("신청 거절 알림", () -> {
            User user = request.getUser();
            String serverName = request.getResourceGroup().getServerName();

            String message = messageUtils.get("notification.admin.request-rejected",
                    String.valueOf(request.getRequestId()),                    // {0}
                    serverName,                                                // {1}
                    SlackText.line(user.getName()),                            // {2}
                    SlackText.line(orNone(adminComment)));                     // {3}
            replyToRequest(message, requestChannel(user, serverName), request.getSlackMessageTs());
        });
    }

    /**
     * 승인한 신청의 컨테이너가 만들어졌음을 신청서 채널에 알린다 — 그 신청서의 스레드 댓글로 달아(모르면 일반 메시지)
     * 승인자가 배정 결과(노드·포트·UID)를 따로 묻거나 관리자가 손으로 적지 않아도 되게 한다.
     * 포트는 문자열로 받는다 — MessageFormat에 숫자형을 주면 30,888처럼 콤마가 붙는다.
     */
    public void sendContainerCreatedNotification(Request request, String sshPort, String jupyterPort) {
        safely("생성 완료 알림", () -> {
            User user = request.getUser();
            var image = request.getContainerImage();
            String serverName = request.getResourceGroup().getServerName();
            LocalDateTime receivedAt = request.getCreatedAt() != null
                    ? request.getCreatedAt()
                    : LocalDateTime.now(ZoneId.of("Asia/Seoul"));

            String message = messageUtils.get("notification.admin.request-fulfilled",
                    String.valueOf(request.getRequestId()),                    // {0}
                    serverName,                                                // {1}
                    RECEIVED_AT_FORMAT.format(receivedAt),                     // {2}
                    SlackText.line(user.getName()),                            // {3}
                    request.getUbuntuUsername(),                               // {4}
                    SlackText.line(request.getNodeName()),                     // {5}
                    image == null ? "-" : SlackText.line(image.getImageName() + ":" + image.getImageVersion()), // {6}
                    serverProfileRegistry.publicPort(serverName, sshPort),     // {7}
                    serverProfileRegistry.publicPort(serverName, jupyterPort), // {8}
                    String.valueOf(user.getUbuntuUid()),                       // {9}
                    String.valueOf(user.getUbuntuGid()));                      // {10}
            replyToRequest(message, requestChannel(user, serverName), request.getSlackMessageTs());
        });
    }

    /**
     * 변경 요청이 들어왔음을 신청서와 같은 블록 양식으로 알린다. 사용 기간 연장은 승인자가 판단하는 신청서 채널로,
     * 나머지(공유 그룹 추가·추가 포트 변경·비밀번호 변경)는 알림 기록(noti) 채널로 간다. 관리자 화면의
     * 변경 요청 관리에서 승인·거절한다. 호출자가 커밋 뒤에 부른다.
     */
    public void sendChangeRequestNotification(ChangeRequestNotice notice) {
        safely("변경 요청 접수 알림", () -> {
            LocalDateTime receivedAt = notice.receivedAt() != null
                    ? notice.receivedAt()
                    : LocalDateTime.now(ZoneId.of("Asia/Seoul"));
            String target = notice.requestId() == null
                    ? "계정"
                    : notice.serverName() + " 신청 #" + notice.requestId();

            String message = messageUtils.get("notification.admin.change-request.received",
                    String.valueOf(notice.changeRequestId()),                  // {0}
                    notice.changeType().label(),                               // {1}
                    RECEIVED_AT_FORMAT.format(receivedAt),                     // {2}
                    SlackText.line(notice.name()),                             // {3}
                    SlackText.line(notice.studentId()),                        // {4}
                    SlackText.line(notice.department()),                       // {5}
                    SlackText.line(notice.email()),                            // {6}
                    SlackText.line(notice.ubuntuUsername()),                   // {7}
                    target,                                                    // {8}
                    notice.change(),                                           // {9}
                    notice.reason() == null ? "없음" : SlackText.quote(notice.reason())); // {10}

            enqueueRequestChannel(message, changeRequestChannel(notice.changeType(), notice.email(), notice.serverName()),
                    null, null, notice.changeRequestId());
        });
    }

    /**
     * 변경 요청의 결과(승인되어 반영 끝남·거절됨)를 접수 알림과 같은 채널에 알린다 — 접수 알림의 스레드 댓글로 달아
     * (모르면 일반 메시지) 채널에 남은 접수 알림이 처리되지 않은 것으로 보이지 않게 한다. 작업으로 반영하는 종류는
     * 승인을 누른 때가 아니라 반영이 끝난 때 부른다. 호출자가 커밋 뒤에 부른다.
     */
    public void sendChangeRequestDecidedNotification(ChangeRequestDecision decision) {
        safely("변경 요청 결과 알림", () -> {
            String message = messageUtils.get(decision.approved()
                            ? "notification.admin.change-request.approved"
                            : "notification.admin.change-request.rejected",
                    String.valueOf(decision.changeRequestId()),                // {0}
                    decision.changeType().label(),                             // {1}
                    SlackText.line(decision.name()),                           // {2}
                    SlackText.line(orNone(decision.adminComment())));          // {3}
            replyToRequest(message,
                    changeRequestChannel(decision.changeType(), decision.email(), decision.serverName()),
                    decision.slackMessageTs());
        });
    }

    /** 신청자가 거둔 변경 요청을 접수 알림의 스레드 댓글로 알린다. 호출자가 커밋 뒤에 부른다. */
    public void sendChangeRequestCancelledNotification(ChangeRequestDecision decision) {
        safely("변경 요청 취소 알림", () -> {
            String message = messageUtils.get("notification.admin.change-request.cancelled",
                    String.valueOf(decision.changeRequestId()),                // {0}
                    decision.changeType().label(),                             // {1}
                    SlackText.line(decision.name()));                          // {2}
            replyToRequest(message,
                    changeRequestChannel(decision.changeType(), decision.email(), decision.serverName()),
                    decision.slackMessageTs());
        });
    }

    /** 변경 요청의 접수 알림과 그 결과가 가는 채널. 승인자가 판단하는 사용 기간 연장만 신청서 채널이고 나머지는 알림 기록 채널이다. */
    private RequestChannel changeRequestChannel(ChangeType changeType, String email, String serverName) {
        return changeType == ChangeType.EXPIRES_AT ? requestChannel(email, serverName) : notiChannel();
    }

    /** 신청서·변경 요청 접수 알림의 후속 알림. 그 메시지의 식별자를 알면 스레드 댓글로, 모르면(webhook으로 보냈거나 아직 적히기 전) 일반 메시지로 보낸다. */
    private void replyToRequest(String message, RequestChannel channel, String threadTs) {
        if (threadTs == null) {
            enqueueWebhook(message, channel.webhookUrl(), true);
            return;
        }
        enqueueRequestChannel(message, channel, threadTs, null, null);
    }

    /** 신청서 채널 한 곳. 채널 ID는 봇으로 올릴 때 쓰고, 없거나 봇 전송이 실패하면 webhook으로 보낸다. */
    private record RequestChannel(String webhookUrl, String channelId) {
    }

    /**
     * 신청서와 그 신청의 취소 알림이 가는 채널. 서버별 신청서 채널로 가고(따로 없으면 관리 채널), 그 채널에는
     * 이 둘 말고 다른 알림을 보내지 않는다. 테스트용 계정의 것은 webhook이든 봇이든 알림 기록 채널로만 돌린다.
     */
    private RequestChannel requestChannel(User user, String serverName) {
        return requestChannel(user.getEmail(), serverName);
    }

    private RequestChannel requestChannel(String email, String serverName) {
        if (isTestAccount(email)) {
            return notiChannel();
        }
        return new RequestChannel(
                serverProfileRegistry.requestWebhookUrl(serverName).orElse(errorLogWebhookUrl),
                serverProfileRegistry.requestChannelId(serverName).orElse(null));
    }

    /** 알림 기록(noti) 채널. 블록 양식 글을 봇으로 올릴 수 있으면 봇으로, 채널 ID가 없으면 webhook으로 보낸다. */
    private RequestChannel notiChannel() {
        return new RequestChannel(notiLogWebhookUrl,
                notiChannelId == null || notiChannelId.isBlank() ? null : notiChannelId);
    }

    /**
     * 신청서 채널로 보낸다. 승인자가 읽고 판단하는 글이라 블록 양식(제목·항목 표·구분선)으로 보낸다. requestId나
     * changeRequestId를 주면 올라간 메시지의 식별자를 그 신청·변경 요청에 적어 두고, threadTs를 주면 그 메시지의
     * 스레드 댓글로 단다. 채널 ID가 없는 채널은 둘 다 할 수 없어 webhook으로 보낸다.
     */
    private void enqueueRequestChannel(String message, RequestChannel channel, String threadTs,
                                       Long requestId, Long changeRequestId) {
        if (channel.channelId() == null) {
            enqueueWebhook(message, channel.webhookUrl(), true);
            return;
        }
        pushToQueue(SlackMessageDto.builder()
                .type(SlackMessageDto.MessageType.CHANNEL)
                .channelId(channel.channelId())
                .threadTs(threadTs)
                .requestId(requestId)
                .changeRequestId(changeRequestId)
                .webhookUrl(channel.webhookUrl())
                .message(message)
                .blockLayout(true)
                .build());
    }

    static boolean isTestAccount(String email) {
        if (email == null) {
            return false;
        }
        int at = email.lastIndexOf('@');
        return at >= 0 && email.substring(at + 1).toLowerCase(Locale.ROOT).startsWith(TEST_EMAIL_DOMAIN_PREFIX);
    }

    private static String describe(String description) {
        return description == null || description.isBlank() ? "" : " (" + SlackText.line(description) + ")";
    }

    private static String formatGroups(Request request) {
        // 승인 대기 그룹은 이 신청을 승인할 때 인프라에 새로 만들어진다 — 승인자가 알 수 있게 표시한다.
        String groups = request.getRequestGroups().stream()
                .map(rg -> rg.getGroup().isPending()
                        ? rg.getGroup().getGroupName() + " (새 그룹 - 승인 시 생성)"
                        : rg.getGroup().getGroupName())
                .sorted()
                .collect(Collectors.joining(", "));
        return groups.isEmpty() ? "없음" : SlackText.line(groups);
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
            return teamInfo.isBlank() ? "없음" : SlackText.line(teamInfo.strip());
        } catch (JsonProcessingException e) {
            return "없음";
        }
    }

    private static String formatPortRequests(List<PortRequests> portRequests) {
        if (portRequests == null || portRequests.isEmpty()) {
            return "없음";
        }
        return portRequests.stream()
                .map(p -> p.getInternalPort() + "번 (" + SlackText.line(p.getUsagePurpose()) + ")")
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

        mailWithReceipt(user.getName(), user.getEmail(), subject, body);
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

        mailWithReceipt(user.getName(), user.getEmail(), subject, body);
    }

    /**
     * [추가 포트 변경 안내 메일] 포트 변경 요청이 컨테이너에 반영됐을 때 보낸다. SSH·Jupyter 접속 정보는 그대로라
     * 바뀐 뒤의 추가 포트만 알린다. 포트는 커밋된 신청의 포트 기록에서 읽고, 생성 안내의 SSH 포트와 같은 방식
     * (포워딩 서버는 공인 포트)으로 적는다.
     */
    public void sendExtraPortsChangedEmail(Request request) {
        User user = request.getUser();
        String serverName = request.getResourceGroup().getServerName();
        List<PodExternalPort> allPorts = podExternalPortRepository.findByRequestRequestId(request.getRequestId());

        String extraPorts = PodPortUtils.extraPorts(allPorts).stream()
                .map(port -> port.getUsagePurpose() + "("
                        + serverProfileRegistry.publicPort(serverName, String.valueOf(port.getExternalPort())) + ")")
                .collect(Collectors.joining(", "));

        String subject = messageUtils.get("email.container.extra-ports-changed.subject", serverName);
        String body = messageUtils.get("email.container.extra-ports-changed.body",
                user.getName(),                                   // {0}
                request.getUbuntuUsername(),                      // {1}
                resolveHostIp(serverName),                        // {2}
                extraPorts.isEmpty() ? "없음" : extraPorts);       // {3}

        mailWithReceipt(user.getName(), user.getEmail(), subject, body);
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

        mailWithReceipt(user.getName(), user.getEmail(), subject, body);
    }

    public void sendRequestRejectedEmail(Request request, String adminComment) {
        User user = request.getUser();
        String serverName = request.getResourceGroup().getServerName();

        String subject = messageUtils.get("email.request.rejected.subject", serverName);
        String body = messageUtils.get("email.request.rejected.body",
                user.getName(),    // {0}
                serverName,        // {1}
                orNone(adminComment)); // {2}

        mailWithReceipt(user.getName(), user.getEmail(), subject, body);
    }

    public void sendModificationRejectedEmail(ChangeRequest changeRequest, String adminComment) {
        User user = changeRequest.getRequestedBy();
        String changeType = changeRequest.getChangeType().label();

        String subject = messageUtils.get("email.modification.rejected.subject", changeType);
        String body = messageUtils.get("email.modification.rejected.body",
                user.getName(),    // {0}
                changeType,        // {1}
                orNone(adminComment)); // {2}

        mailWithReceipt(user.getName(), user.getEmail(), subject, body);
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

        mailWithReceipt(user.getName(), user.getEmail(), subject, body);
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

        mailWithReceipt(user.getName(), user.getEmail(), subject, body);
    }

    /** 관리자가 메모를 비워 두면 MessageFormat이 "null"을 찍으므로 "없음"으로 바꾼다. */
    private static String orNone(String text) {
        return text == null || text.isBlank() ? "없음" : text;
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
            if (dto.getType() == SlackMessageDto.MessageType.DM) {
                slackApiService.sendDM(dto.getUsername(), dto.getEmail(), fullMessage);
            } else {
                // 봇으로 올릴 글(CHANNEL)도 큐가 죽은 동안에는 webhook으로 보낸다 — 스레드는 포기하고 전달만 지킨다.
                slackApiService.sendWebhook(dto.getWebhookUrl(), fullMessage);
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