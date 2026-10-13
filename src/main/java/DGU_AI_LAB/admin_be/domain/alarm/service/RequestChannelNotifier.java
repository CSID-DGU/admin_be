package DGU_AI_LAB.admin_be.domain.alarm.service;

import DGU_AI_LAB.admin_be.domain.alarm.SlackText;
import DGU_AI_LAB.admin_be.domain.alarm.dto.ChangeRequestDecision;
import DGU_AI_LAB.admin_be.domain.alarm.dto.ChangeRequestNotice;
import DGU_AI_LAB.admin_be.domain.alarm.dto.SlackMessageDto;
import DGU_AI_LAB.admin_be.domain.portRequests.entity.PortRequests;
import DGU_AI_LAB.admin_be.domain.requests.entity.ChangeType;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.global.server.ServerProfileRegistry;
import DGU_AI_LAB.admin_be.global.util.MessageUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * 결정 요청: 승인·거절이 필요한 신청과 변경 요청을 신청서 채널에 올리고, 그 뒤의 변화(취소·거절·생성 완료,
 * 변경 요청의 승인 완료·거절)를 접수 알림 메시지의 스레드 댓글로 단다. 어느 채널로 갈지도 여기서 정한다.
 * 전송 실패는 여기서 잡아 기록한다 — 알림이 실패해도 호출한 쪽의 처리는 실패하지 않는다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RequestChannelNotifier {

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

    private final MessageUtils messageUtils;
    private final ServerProfileRegistry serverProfileRegistry;
    private final SlackNotificationQueue queue;
    private final NotificationChannels channels;

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
                    SlackText.line(NotificationText.orNone(adminComment)));                     // {3}
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
                    SlackText.line(NotificationText.orNone(decision.adminComment())));          // {3}
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
            queue.webhook(message, channel.webhookUrl(), true);
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
                serverProfileRegistry.requestWebhookUrl(serverName).orElse(channels.errorWebhookUrl()),
                serverProfileRegistry.requestChannelId(serverName).orElse(null));
    }

    /** 알림 기록(noti) 채널. 블록 양식 글을 봇으로 올릴 수 있으면 봇으로, 채널 ID가 없으면 webhook으로 보낸다. */
    private RequestChannel notiChannel() {
        return new RequestChannel(channels.notiWebhookUrl(), channels.notiChannelId());
    }

    /**
     * 신청서 채널로 보낸다. 승인자가 읽고 판단하는 글이라 블록 양식(제목·항목 표·구분선)으로 보낸다. requestId나
     * changeRequestId를 주면 올라간 메시지의 식별자를 그 신청·변경 요청에 적어 두고, threadTs를 주면 그 메시지의
     * 스레드 댓글로 단다. 채널 ID가 없는 채널은 둘 다 할 수 없어 webhook으로 보낸다.
     */
    private void enqueueRequestChannel(String message, RequestChannel channel, String threadTs,
                                       Long requestId, Long changeRequestId) {
        if (channel.channelId() == null) {
            queue.webhook(message, channel.webhookUrl(), true);
            return;
        }
        queue.push(SlackMessageDto.builder()
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

    private void safely(String what, Runnable send) {
        try {
            send.run();
        } catch (Exception e) {
            log.error("{} 전송 실패", what, e);
        }
    }
}
