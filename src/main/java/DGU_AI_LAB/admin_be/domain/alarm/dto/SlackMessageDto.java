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
        DM       // 사용자 개인 DM
    }

    private MessageType type;   // 메시지 타입 구분
    private String message;     // 보낼 메시지 내용

    // Webhook용 필드
    private String webhookUrl;

    // DM용 필드
    private String username;
    private String email;
}