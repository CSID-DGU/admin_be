package DGU_AI_LAB.admin_be.domain.alarm.controller.docs;

import DGU_AI_LAB.admin_be.domain.alarm.dto.request.CombinedAlertRequestDTO;
import DGU_AI_LAB.admin_be.domain.alarm.dto.request.EmailRequestDTO;
import DGU_AI_LAB.admin_be.domain.alarm.dto.request.SlackDMRequestDTO;
import DGU_AI_LAB.admin_be.global.common.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;

@Tag(name = "Slack 및 E-mail", description = "Slack 및 Email 알림 API")
public interface AlarmApi {

    @Operation(
            summary = "Slack DM 알림 전송",
            description = "Slack 사용자에게 개인 DM으로 알림 메시지를 전송합니다. 이름이 중복될 경우 이메일이 일치하는 사용자에게 전송됩니다."
    )
    @ApiResponse(
            responseCode = "200", description = "Slack DM 전송 성공",
            content = @Content(schema = @Schema(implementation = SuccessResponse.class))
    )
    ResponseEntity<?> sendSlackDMAlert(
            @RequestBody(description = "Slack DM 알림 요청 DTO", required = true)
            @Valid SlackDMRequestDTO request
    );

    @Operation(
            summary = "Email 알림 전송",
            description = "Email로 알림 메시지를 전송합니다."
    )
    @ApiResponse(
            responseCode = "200", description = "Email 전송 성공",
            content = @Content(schema = @Schema(implementation = SuccessResponse.class))
    )
    ResponseEntity<?> sendEmailAlert(
            @RequestBody(description = "이메일 알림 요청 DTO", required = true)
            @Valid EmailRequestDTO request
    );

    @Operation(
            summary = "Slack DM + Email 통합 알림 전송",
            description = "Slack DM과 Email을 동시에 전송합니다."
    )
    @ApiResponse(
            responseCode = "200", description = "Slack + Email 알림 전송 성공",
            content = @Content(schema = @Schema(implementation = SuccessResponse.class))
    )
    ResponseEntity<?> sendAllAlerts(
            @RequestBody(description = "통합 알림 요청 DTO", required = true)
            @Valid CombinedAlertRequestDTO request
    );
}