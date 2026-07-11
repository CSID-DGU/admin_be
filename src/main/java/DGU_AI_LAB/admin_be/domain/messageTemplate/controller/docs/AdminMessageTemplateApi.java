package DGU_AI_LAB.admin_be.domain.messageTemplate.controller.docs;

import DGU_AI_LAB.admin_be.error.dto.ErrorResponse;
import DGU_AI_LAB.admin_be.global.common.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@Tag(name = "3. 관리자 시스템", description = "컨테이너 이미지, K8s Pod, 알림 템플릿 관리 API")
public interface AdminMessageTemplateApi {

    @Operation(summary = "알림 메시지 템플릿 전체 조회", description = "모든 알림 메시지 키와 현재 값(기본값 또는 오버라이드)을 조회합니다.")
    @ApiResponse(responseCode = "200", description = "성공")
    @ApiResponse(responseCode = "403", description = "관리자 권한 없음",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping
    ResponseEntity<SuccessResponse<?>> getAll();

    @Operation(
            summary = "알림 메시지 템플릿 수정",
            description = "지정한 키의 메시지를 수정합니다. 수정값은 DB에 오버라이드로 저장되며 기본값(messages.properties)보다 우선 적용됩니다. " +
                    "키에 점(.)이 포함됩니다 (예: notification.expired.dm)."
    )
    @ApiResponse(responseCode = "200", description = "수정 성공")
    @ApiResponse(responseCode = "403", description = "관리자 권한 없음",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "존재하지 않는 메시지 키",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PatchMapping("/{key}")
    ResponseEntity<SuccessResponse<?>> update(
            @PathVariable @Parameter(description = "메시지 키 (예: notification.expired.dm)", example = "notification.expired.dm") String key,
            @RequestBody Map<String, String> body
    );

    @Operation(
            summary = "알림 메시지 템플릿 초기화",
            description = "지정한 키의 DB 오버라이드를 삭제하여 messages.properties의 기본값으로 복원합니다."
    )
    @ApiResponse(responseCode = "200", description = "초기화 성공")
    @ApiResponse(responseCode = "403", description = "관리자 권한 없음",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @DeleteMapping("/{key}")
    ResponseEntity<SuccessResponse<?>> reset(
            @PathVariable @Parameter(description = "초기화할 메시지 키", example = "notification.expired.dm") String key
    );
}
