package DGU_AI_LAB.admin_be.domain.alarm.controller.docs;

import DGU_AI_LAB.admin_be.global.common.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestParam;

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
            @Parameter(description = "Slack 사용자 이름")
            @RequestParam @NotBlank String username,

            @Parameter(description = "Slack 사용자 이메일")
            @RequestParam @Email String email,

            @Parameter(description = "전송할 메시지")
            @RequestParam @NotBlank String message
    );
}
