package DGU_AI_LAB.admin_be.domain.alarm.service;

import DGU_AI_LAB.admin_be.domain.pod.PodPortUtils;
import DGU_AI_LAB.admin_be.domain.pod.entity.PodExternalPort;
import DGU_AI_LAB.admin_be.domain.pod.repository.PodExternalPortRepository;
import DGU_AI_LAB.admin_be.domain.requests.entity.ChangeRequest;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.global.server.ServerProfileRegistry;
import DGU_AI_LAB.admin_be.global.util.MessageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 사용자에게 메일로만 보내는 안내의 문구를 만든다(접속 정보처럼 Slack DM으로 보내지 않는 것). 보내고 기록을 남기는
 * 일은 {@link AlarmService#mailUser}가 한다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserGuideMailer {

    private final MessageUtils messageUtils;
    private final PodExternalPortRepository podExternalPortRepository;
    private final ServerProfileRegistry serverProfileRegistry;
    private final AlarmService alarmService;

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

        alarmService.mailUser(user.getName(), user.getEmail(), subject, body);
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

        alarmService.mailUser(user.getName(), user.getEmail(), subject, body);
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

        alarmService.mailUser(user.getName(), user.getEmail(), subject, body);
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

        alarmService.mailUser(user.getName(), user.getEmail(), subject, body);
    }

    /** Slack 회원이 아니라서 신청을 받지 않았을 때, 무엇을 고쳐야 하는지 알린다. 신청이 만들어지지 않았으므로 사람만 받는다. */
    public void sendSlackMembershipRequiredEmail(String name, String email) {
        String subject = messageUtils.get("email.request.slack-required.subject");
        String body = messageUtils.get("email.request.slack-required.body",
                name,   // {0}
                email); // {1}

        alarmService.mailUser(name, email, subject, body);
    }

    public void sendRequestRejectedEmail(Request request, String adminComment) {
        User user = request.getUser();
        String serverName = request.getResourceGroup().getServerName();

        String subject = messageUtils.get("email.request.rejected.subject", serverName);
        String body = messageUtils.get("email.request.rejected.body",
                user.getName(),    // {0}
                serverName,        // {1}
                NotificationText.orNone(adminComment)); // {2}

        alarmService.mailUser(user.getName(), user.getEmail(), subject, body);
    }

    public void sendModificationRejectedEmail(ChangeRequest changeRequest, String adminComment) {
        User user = changeRequest.getRequestedBy();
        String changeType = changeRequest.getChangeType().label();

        String subject = messageUtils.get("email.modification.rejected.subject", changeType);
        String body = messageUtils.get("email.modification.rejected.body",
                user.getName(),    // {0}
                changeType,        // {1}
                NotificationText.orNone(adminComment)); // {2}

        alarmService.mailUser(user.getName(), user.getEmail(), subject, body);
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
                NotificationText.orNone(adminComment)); // {2}

        alarmService.mailUser(user.getName(), user.getEmail(), subject, body);
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
                NotificationText.orNone(adminComment), // {2}
                shareCommands);    // {3}

        alarmService.mailUser(user.getName(), user.getEmail(), subject, body);
    }
}
