package DGU_AI_LAB.admin_be.domain.monitoring.controller.docs;

import DGU_AI_LAB.admin_be.domain.monitoring.dto.response.AdminGpuUsageResponseDTO;
import DGU_AI_LAB.admin_be.error.dto.ErrorResponse;
import DGU_AI_LAB.admin_be.global.common.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "9. 관리자 GPU 사용 현황", description = "관리자용 노드별 GPU 사용률·프로세스·소유자 조회 API")
@RequestMapping("/api/admin/gpu-usage")
public interface AdminGpuUsageApi {

    @Operation(summary = "노드별 GPU 사용 현황 조회",
            description = "GPU별 사용률·메모리와 그 위에서 도는 프로세스(소유자 이름·계정·신청 번호 포함)를 노드별로 조회합니다. "
                    + "node를 생략하면 DB의 모든 노드입니다. exporter가 없으면 processesAvailable=false이고 프로세스는 비어 있습니다. "
                    + "소유자를 못 찾은 프로세스는 ownerName 등이 null입니다.")
    @ApiResponse(responseCode = "200", description = "성공",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = AdminGpuUsageResponseDTO.class))))
    @ApiResponse(responseCode = "401", description = "인증되지 않은 사용자",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "관리자가 아님",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "DB에 없는 노드",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "502", description = "노드 하나를 지정했는데 GPU 지표 조회에 실패",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping
    ResponseEntity<SuccessResponse<?>> getGpuUsage(
            @Parameter(description = "노드 이름(생략하면 전체)", example = "farm6")
            @RequestParam(name = "node", required = false) String node
    );
}
