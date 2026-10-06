package DGU_AI_LAB.admin_be.domain.alarm.dto; // 패키지 위치 확인

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SlackMessageDto implements Serializable {

    public enum MessageType {
        WEBHOOK, // 관리자 채널 알림
        DM,      // 사용자 개인 DM
        CHANNEL  // 봇이 채널에 올리는 글(스레드 댓글 포함). 실패하면 webhookUrl로 대신 보낸다
    }

    private MessageType type;
    private String message;
    /** true면 글을 Slack 블록 양식(제목·항목 표·구분선)으로 바꿔 보낸다. WEBHOOK·CHANNEL에만 쓴다. */
    private boolean blockLayout;

    // Webhook용 필드
    private String webhookUrl;

    // CHANNEL용 필드
    private String channelId;
    /** 있으면 그 메시지의 스레드 댓글로 단다. */
    private String threadTs;
    /** 있으면 올라간 메시지의 식별자(ts)를 이 신청에 적어 둔다. */
    private Long requestId;

    // DM용 필드
    private String username;
    private String email;

    @Builder.Default
    private int retryCount = 0;

    public void incrementRetryCount() {
        this.retryCount++;
    }
}