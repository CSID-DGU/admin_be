package DGU_AI_LAB.admin_be.domain.alarm.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "인프라 Slack 알림 요청 DTO")
public record InfraSlackRequestDTO(

        @NotBlank(message = "webhookUrl은 필수입니다.")
        @Schema(description = "전송할 Slack Webhook URL", example = "https://hooks.slack.com/services/...")
        String webhookUrl,

        @NotBlank(message = "message는 필수입니다.")
        @Schema(description = "전송할 메시지", example = "🚨 GPU 사용률 95% 초과")
        String message
) {}
