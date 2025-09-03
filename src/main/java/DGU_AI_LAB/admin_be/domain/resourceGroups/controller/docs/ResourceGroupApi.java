package DGU_AI_LAB.admin_be.domain.resourceGroups.controller.docs;

import DGU_AI_LAB.admin_be.domain.gpus.dto.response.GpuTypeResponseDTO;
import DGU_AI_LAB.admin_be.error.dto.ErrorResponse;
import DGU_AI_LAB.admin_be.global.common.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Tag(name = "2. 리소스 그룹 관리 API", description = "GPU 기종별 리소스 정보 조회 등 리소스 그룹 관련 API")
@RequestMapping("/api/resources")
public interface ResourceGroupApi {

    @Operation(
            summary = "GPU 기종별 리소스 정보 조회",
            description = "서버 신청 시 선택 가능한 GPU 기종별(리소스 그룹) 리소스 현황을 조회합니다.<br>" +
                    "현재 모든 노드는 사용 가능하다고 가정합니다.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "성공적으로 GPU 기종별 리소스 정보를 조회했습니다.",
                            content = @Content(schema = @Schema(implementation = GpuTypeResponseDTO.class))
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "사용 가능한 리소스가 없습니다. (NO_AVAILABLE_RESOURCES)",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
                    ),
                    @ApiResponse(
                            responseCode = "500",
                            description = "서버 내부 오류 발생.",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
                    )
            }
    )
    @GetMapping("/gpu-types")
    ResponseEntity<SuccessResponse<?>> getGpuTypeResources();
}