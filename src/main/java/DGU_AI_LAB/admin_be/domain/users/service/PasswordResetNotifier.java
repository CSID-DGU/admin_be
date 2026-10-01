package DGU_AI_LAB.admin_be.domain.users.service;

import DGU_AI_LAB.admin_be.domain.alarm.SlackText;
import DGU_AI_LAB.admin_be.domain.alarm.service.AlarmService;
import DGU_AI_LAB.admin_be.domain.users.dto.response.PasswordResetSummaryDTO;
import DGU_AI_LAB.admin_be.global.util.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 비밀번호 재설정 신청의 진행을 알린다 — 신청자에게는 메일, 관리자에게는 Slack.
 * 알림은 상태를 바꾼 뒤의 부가 동작이라, 실패해도 예외를 밖으로 내지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PasswordResetNotifier {

    private final EmailService emailService;
    private final AlarmService alarmService;

    /** 승인을 기다리는 신청이 새로 들어왔다. */
    public void requested(PasswordResetSummaryDTO request) {
        slack(String.format("[비밀번호 재설정] 새 신청 — 관리자 화면의 사용자 관리에서 승인하거나 거절해 주세요: resetId=%d, %s(%s)",
                request.passwordResetRequestId(), SlackText.escape(request.name()), SlackText.escape(request.email())));
    }

    public void applied(String email) {
        try {
            emailService.sendPasswordChangedNotice(email);
        } catch (RuntimeException e) {
            log.warn("[passwordReset] 비밀번호 변경 알림 메일 발송 실패", e);
        }
    }

    public void denied(String email) {
        try {
            emailService.sendPasswordResetDeniedNotice(email);
        } catch (RuntimeException e) {
            log.warn("[passwordReset] 재설정 거절 알림 메일 발송 실패", e);
        }
    }

    /**
     * 컨테이너 반영 작업이 성공하지 못해 신청을 승인 대기로 되돌렸다.
     *
     * @param mayBePartial 일부 컨테이너에만 새 비밀번호가 들어갔을 수 있는가(되돌리지 못했거나 결과를 모르는 실패)
     */
    public void jobFailed(PasswordResetSummaryDTO request, String phase, String errorCode, boolean mayBePartial) {
        String advice = mayBePartial
                ? "일부 컨테이너에만 새 비밀번호가 들어갔을 수 있습니다. 거절하지 말고 다시 승인해 모두 맞춰 주세요"
                : "다시 승인하면 같은 비밀번호로 다시 반영합니다";
        slack(String.format("[비밀번호 재설정] 컨테이너 반영 실패 — 승인 대기로 되돌림. %s: resetId=%d, userId=%d, phase=%s, error=%s",
                advice, request.passwordResetRequestId(), request.userId(), phase, errorCode));
    }

    private void slack(String message) {
        try {
            alarmService.sendSlackAlert(message, null);
        } catch (RuntimeException e) {
            log.warn("[passwordReset] Slack 알림 전송 실패", e);
        }
    }
}
