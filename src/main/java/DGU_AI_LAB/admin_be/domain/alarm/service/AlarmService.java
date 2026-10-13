package DGU_AI_LAB.admin_be.domain.alarm.service;

import DGU_AI_LAB.admin_be.domain.alarm.SlackText;
import DGU_AI_LAB.admin_be.global.util.EmailAddresses;
import DGU_AI_LAB.admin_be.global.util.MessageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;

/**
 * [알림 통합 관리 서비스]
 * 시스템 내 알림(Slack, Email) 발송 요청의 진입점 역할을 합니다.
 *
 * <p>어디로 보낼지는 알림의 성격으로만 정한다. 호출부는 채널을 고르지 않고 아래 가운데 하나를 부른다.
 * <ul>
 *   <li>결정 요청(승인·거절이 필요한 신청)과 그 후속 변화 → 신청서 채널: {@link RequestChannelNotifier}</li>
 *   <li>조치 필요(실패·멈춤·수동 확인) → 오류 채널 한 곳: {@link #alertNeedsAction}</li>
 *   <li>처리 기록(정상 완료) → 알림 기록(noti) 채널: {@link #recordLog}</li>
 *   <li>사용자 안내 → 메일 + Slack DM + 알림 기록 채널의 발송 기록: {@link #notifyUser},
 *       메일만 보내는 안내는 {@link #mailUser}(문구는 {@link UserGuideMailer})</li>
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

    private final SlackNotificationQueue queue;
    private final NotificationMailer mailer;
    private final NotificationChannels channels;
    private final MessageUtils messageUtils;

    /**
     * 조치 필요: 실패했거나 멈췄거나 사람이 확인해야 하는 일. 오류 채널 한 곳으로만 보낸다.
     * 로그에는 어떤 알림인지(키)만 남긴다 — 값에는 이름·메일 주소가 들어올 수 있다.
     */
    public void alertNeedsAction(String messageKey, Object... args) {
        safely("조치 필요 알림", () -> {
            log.warn("[조치 필요] {}", messageKey);
            queue.webhook(render(messageKey, args), channels.errorWebhookUrl());
        });
    }

    /** 처리 기록: 정상적으로 끝난 일을 나중에 되짚어 볼 수 있게 알림 기록(noti) 채널에 남긴다. */
    public void recordLog(String messageKey, Object... args) {
        safely("처리 기록 알림", () -> queue.webhook(render(messageKey, args), channels.notiWebhookUrl()));
    }

    /**
     * 사용자 안내: 메일과 Slack DM으로 보내고, 보냈다는 기록(본문 없음)을 알림 기록 채널에 남긴다.
     * 메일이 실패하면 기록 대신 오류 채널로 알린다. DM은 메일 성패와 상관없이 보낸다.
     */
    public void notifyUser(String username, String email, String subject, String body) {
        safely("사용자 안내", () -> {
            queue.dm(username, email, body);
            mailUser(username, email, subject, body);
        });
    }

    /** 메일만 보내는 안내(접속 정보처럼 DM으로 보내지 않는 것). 발송 기록 규칙은 {@link #notifyUser}와 같다. */
    public void mailUser(String username, String email, String subject, String body) {
        if (mailer.send(email, subject, body)) {
            queue.webhook(render("notification.monitor.log", username, email, subject), channels.notiWebhookUrl());
        } else {
            alertNeedsAction("notification.admin.mail-failed", EmailAddresses.mask(email));
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
}
