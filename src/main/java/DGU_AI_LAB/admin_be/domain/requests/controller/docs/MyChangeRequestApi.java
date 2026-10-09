package DGU_AI_LAB.admin_be.domain.requests.controller.docs;

import DGU_AI_LAB.admin_be.domain.requests.dto.request.SingleChangeRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.ChangeRequestResponseDTO;
import DGU_AI_LAB.admin_be.error.dto.ErrorResponse;
import DGU_AI_LAB.admin_be.global.common.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

import java.util.List;

@Tag(name = "2-1. 사용자 변경 요청", description = "내 변경 요청 생성 및 조회 API")
public interface MyChangeRequestApi {

    @Operation(summary = "변경 요청 생성", description = "승인된 신청(FULFILLED)의 사용 기간 연장(EXPIRES_AT)·공유 그룹 추가(GROUP)·"
            + "추가 포트 변경(PORT)을 요청합니다. 사유는 100자 이상이어야 합니다. "
            + "비밀번호 변경(PASSWORD)은 메일 인증이 필요해 POST /api/auth/password-resets 로 냅니다.")
    @ApiResponse(responseCode = "201", description = "변경 요청 생성 성공")
    @ApiResponse(responseCode = "400", description = "FULFILLED 상태가 아닌 신청이거나 값·사유가 올바르지 않음",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "본인 소유의 신청이 아님",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "신청을 찾을 수 없음",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "같은 종류의 변경 요청이 이미 대기 중",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    ResponseEntity<SuccessResponse<?>> createChangeRequest(@Parameter(hidden = true) Long userId, SingleChangeRequestDTO dto);

    @Operation(summary = "내 변경 요청 목록 조회", description = "로그인된 사용자의 모든 변경 요청(전체 상태, 비밀번호 변경 포함)을 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공",
            content = @Content(schema = @Schema(implementation = ChangeRequestListResponseDoc.class)))
    ResponseEntity<SuccessResponse<?>> getMyChangeRequests(@Parameter(hidden = true) Long userId);

    // 실제 응답은 SuccessResponse<T> 래퍼로 감싸져 나가므로, Swagger 스키마도 래퍼 형태로 노출한다.
    @Schema(name = "SuccessResponseListChangeRequestResponseDTO", description = "변경 요청 목록 응답")
    record ChangeRequestListResponseDoc(int status, String message, List<ChangeRequestResponseDTO> data) {}
}
