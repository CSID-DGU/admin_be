package DGU_AI_LAB.admin_be.domain.alarm.controller;

import DGU_AI_LAB.admin_be.domain.alarm.dto.InfraSlackRequestDTO;
import DGU_AI_LAB.admin_be.domain.alarm.service.InfraAlarmService;
import DGU_AI_LAB.admin_be.global.common.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "0. 인프라 내부 API", description = "인프라 서버 전용 내부 API (인증 불필요, 내부망 전용)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/internal")
public class InfraSlackController {

    private final InfraAlarmService infraAlarmService;

    @Operation(
            summary = "인프라 Slack 알림 큐잉",
            description = "인프라 서버에서 호출. 지정한 Webhook URL로 메시지를 Redis 큐에 적재하며, " +
                    "채널당 초당 1회 제한을 준수하여 순차 전송합니다. " +
                    "이 엔드포인트는 인증이 필요 없으며 내부망에서만 접근 가능해야 합니다."
    )
    @ApiResponse(responseCode = "200", description = "큐 적재 성공")
    @ApiResponse(responseCode = "400", description = "webhookUrl 또는 message가 누락된 경우")
    @PostMapping("/slack/notify")
    public ResponseEntity<SuccessResponse<?>> notifySlack(
            @RequestBody @Valid InfraSlackRequestDTO dto
    ) {
        infraAlarmService.enqueue(dto.webhookUrl(), dto.message());
        return SuccessResponse.ok(null);
    }
}
