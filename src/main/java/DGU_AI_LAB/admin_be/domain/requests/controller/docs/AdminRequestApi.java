package DGU_AI_LAB.admin_be.domain.requests.controller.docs;

import DGU_AI_LAB.admin_be.domain.requests.dto.request.ApproveRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.RejectRequestDTO;
import DGU_AI_LAB.admin_be.error.dto.ErrorResponse;
import DGU_AI_LAB.admin_be.global.common.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "1. 관리자 - 서버 사용 신청 관리", description = "신규 신청 조회 및 승인/거절 API")
public interface AdminRequestApi {

    @Operation(summary = "모든 요청 목록 조회", description = "모든 상태의 서버 사용 신청 목록을 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = SuccessResponse.class),
                            examples = @ExampleObject(value = "{\"status\": 200, \"message\": \"요청이 성공했습니다.\", \"data\": [...]}")
                    )),
            @ApiResponse(responseCode = "500", description = "서버 오류",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<SuccessResponse<?>> getAllRequests();

    @Operation(summary = "신규 신청 목록 조회", description = "PENDING 상태의 서버 사용 신청 목록을 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공",
                    content = @Content(schema = @Schema(implementation = SuccessResponse.class)))
    })
    ResponseEntity<SuccessResponse<?>> getNewRequests();

    @Operation(summary = "전체 리소스 사용량 조회", description = "FULFILLED 상태인 모든 서버의 리소스 사용량 정보를 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공",
                    content = @Content(schema = @Schema(implementation = SuccessResponse.class)))
    })
    ResponseEntity<SuccessResponse<?>> getAllResourceUsage();

    @Operation(summary = "모든 활성 컨테이너 조회", description = "현재 활성화된 모든 컨테이너 정보를 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공",
                    content = @Content(schema = @Schema(implementation = SuccessResponse.class)))
    })
    ResponseEntity<SuccessResponse<?>> getAllActiveContainers();

    @Operation(summary = "사용 신청 승인", description = "PENDING 상태의 사용 신청을 승인하고 사용자 PVC를 생성합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공",
                    content = @Content(schema = @Schema(implementation = SuccessResponse.class))),
            @ApiResponse(responseCode = "404", description = "리소스 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "사용자명 중복",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "서버 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<SuccessResponse<?>> approveRequest(ApproveRequestDTO dto);

    @Operation(summary = "사용 신청 거절", description = "PENDING 상태의 사용 신청을 거절합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공",
                    content = @Content(schema = @Schema(implementation = SuccessResponse.class))),
            @ApiResponse(responseCode = "404", description = "리소스 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<SuccessResponse<?>> rejectRequest(RejectRequestDTO dto);
}