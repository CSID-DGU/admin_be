package DGU_AI_LAB.admin_be.domain.requests.controller.docs;

import DGU_AI_LAB.admin_be.domain.requests.dto.request.ApproveModificationDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.RejectModificationDTO;
import DGU_AI_LAB.admin_be.error.dto.ErrorResponse;
import DGU_AI_LAB.admin_be.global.common.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "3. 관리자 변경 요청 관리", description = "서버 설정 변경 요청 조회 및 승인·거절 API")
public interface AdminRequestChangeApi {

    @Operation(summary = "대기 중인 변경 요청 목록 조회", description = "PENDING 상태의 변경 요청 목록을 조회합니다.")
    @ApiResponse(responseCode = "200", description = "성공")
    ResponseEntity<SuccessResponse<?>> getChangeRequests();

    @Operation(summary = "전체 변경 요청 목록 조회", description = "모든 상태(PENDING, APPROVED, DENIED)의 변경 요청 목록을 조회합니다.")
    @ApiResponse(responseCode = "200", description = "성공")
    ResponseEntity<SuccessResponse<?>> getAllChangeRequests();

    @Operation(summary = "변경 요청 승인", description = "PENDING 상태의 변경 요청을 승인하고 원본 신청의 설정을 업데이트합니다.")
    @ApiResponse(responseCode = "200", description = "성공")
    @ApiResponse(responseCode = "400", description = "PENDING 상태가 아닌 변경 요청",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "변경 요청 또는 관리자 계정을 찾을 수 없음",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    ResponseEntity<SuccessResponse<?>> approveModification(Long adminId, ApproveModificationDTO dto);

    @Operation(summary = "변경 요청 거절", description = "PENDING 상태의 변경 요청을 거절합니다.")
    @ApiResponse(responseCode = "200", description = "성공")
    @ApiResponse(responseCode = "400", description = "PENDING 상태가 아닌 변경 요청",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "변경 요청 또는 관리자 계정을 찾을 수 없음",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    ResponseEntity<SuccessResponse<?>> rejectModification(Long adminId, RejectModificationDTO dto);
}