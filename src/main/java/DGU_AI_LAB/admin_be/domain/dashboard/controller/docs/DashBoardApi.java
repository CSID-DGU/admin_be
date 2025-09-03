package DGU_AI_LAB.admin_be.domain.dashboard.controller.docs;

import DGU_AI_LAB.admin_be.domain.requests.dto.response.UserServerResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.error.dto.ErrorResponse;
import DGU_AI_LAB.admin_be.global.auth.CustomUserDetails;
import DGU_AI_LAB.admin_be.global.common.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;


@Tag(name = "2. 사용자 대시보드 API", description = "대시보드용 API")
@RequestMapping("/api/dashboard")
public interface DashBoardApi {

    @Operation(
            summary = "사용자 서버 신청 목록 조회",
            description = "현재 로그인한 사용자의 서버 신청 목록을 상태별로 필터링하여 조회합니다.<br>" +
                    "JWT를 통해 사용자 ID를 식별합니다.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "성공적으로 서버 신청 목록을 조회했습니다.",
                            content = @Content(schema = @Schema(implementation = UserServerResponseDTO.class))
                    ),
                    @ApiResponse(
                            responseCode = "401",
                            description = "인증되지 않은 사용자입니다.",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "사용자를 찾을 수 없거나 해당 사용자의 신청 내역이 없습니다. (USER_NOT_FOUND)",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
                    ),
                    @ApiResponse(
                            responseCode = "500",
                            description = "서버 내부 오류 발생.",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
                    )
            }
    )
    @GetMapping("/me/servers")
    ResponseEntity<SuccessResponse<?>> getUserServers(
            @Parameter(hidden = true)
            @AuthenticationPrincipal CustomUserDetails principal,
            @Parameter(description = "조회할 서버 신청 상태 (PENDING, FULFILLED, DENIED 등)", required = true, example = "FULFILLED")
            @RequestParam(name = "status") Status status
    );
}