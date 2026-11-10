package DGU_AI_LAB.admin_be.domain.dashboard.controller.docs;

import DGU_AI_LAB.admin_be.domain.monitoring.dto.response.MyGpuUsageResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.UserServerResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.entity.StatusFilter;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;


@Tag(name = "2. 사용자 대시보드", description = "사용자 신청 현황 대시보드 API")
@RequestMapping("/api/dashboard")
public interface DashBoardApi {

    @Operation(summary = "내 서버 신청 목록 조회", description = "상태(status)로 필터링하여 로그인된 사용자의 서버 신청 목록을 조회합니다.")
    @ApiResponse(responseCode = "200", description = "성공",
            content = @Content(schema = @Schema(implementation = UserServerResponseDTO.class)))
    @ApiResponse(responseCode = "401", description = "인증되지 않은 사용자",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/me/servers")
    ResponseEntity<SuccessResponse<?>> getUserServers(
            @Parameter(hidden = true)
            @AuthenticationPrincipal CustomUserDetails principal,
            @Parameter(description = "조회할 서버 신청 상태 필터 (PENDING, FULFILLED, DENIED, DELETED, ALL)", required = true, example = "FULFILLED")
            @RequestParam(name = "status") StatusFilter statusFilter
    );

    @Operation(summary = "내 서버 GPU 사용 현황 조회",
            description = "내 서버(FULFILLED 신청)의 GPU별 사용률·메모리와 내 프로세스를 조회합니다. 다른 사용자는 사람 수와 메모리 합계로만 담기며 식별 정보는 없습니다. "
                    + "프로세스 정보를 읽지 못하면 processesAvailable=false, GPU 지표를 읽지 못하면 502입니다.")
    @ApiResponse(responseCode = "200", description = "성공",
            content = @Content(schema = @Schema(implementation = MyGpuUsageResponseDTO.class)))
    @ApiResponse(responseCode = "401", description = "인증되지 않은 사용자",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "다른 사용자의 신청",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "없는 신청이거나 실행 중(FULFILLED)이 아닌 신청",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "502", description = "GPU 지표 조회 실패",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/me/servers/{requestId}/gpus")
    ResponseEntity<SuccessResponse<?>> getMyGpuUsage(
            @Parameter(hidden = true)
            @AuthenticationPrincipal CustomUserDetails principal,
            @Parameter(description = "신청 ID", required = true, example = "1")
            @PathVariable Long requestId
    );
}