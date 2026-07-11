package DGU_AI_LAB.admin_be.domain.requests.controller.docs;

import DGU_AI_LAB.admin_be.domain.requests.dto.request.ApproveRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.RejectRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.ContainerInfoDTO;
import DGU_AI_LAB.admin_be.error.dto.ErrorResponse;
import DGU_AI_LAB.admin_be.global.common.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

import java.util.List;

@Tag(name = "3. 관리자 신청 관리", description = "서버 사용 신청 승인·거절 및 컨테이너 현황 조회 API")
public interface AdminRequestApi {

    @Operation(summary = "전체 신청 목록 조회", description = "모든 상태의 서버 사용 신청 목록을 조회합니다.")
    @ApiResponse(responseCode = "200", description = "성공")
    ResponseEntity<SuccessResponse<?>> getAllRequests();

    @Operation(summary = "신규 신청 목록 조회", description = "PENDING 상태의 신청 목록을 조회합니다.")
    @ApiResponse(responseCode = "200", description = "성공")
    ResponseEntity<SuccessResponse<?>> getNewRequests();

    @Operation(summary = "전체 리소스 사용량 조회", description = "FULFILLED 상태인 모든 서버의 리소스 사용량을 조회합니다.")
    @ApiResponse(responseCode = "200", description = "성공")
    ResponseEntity<SuccessResponse<?>> getAllResourceUsage();

    @Operation(summary = "활성 컨테이너 목록 조회", description = "현재 활성화된 모든 컨테이너 정보(ubuntuUsername, Pod명, 노드명 등)를 조회합니다.")
    @ApiResponse(responseCode = "200", description = "성공",
            content = @Content(schema = @Schema(implementation = ContainerListResponseDoc.class)))
    ResponseEntity<SuccessResponse<?>> getAllActiveContainers();

    @Schema(name = "SuccessResponseListContainerInfoDTO", description = "활성 컨테이너 목록 응답")
    record ContainerListResponseDoc(int status, String message, List<ContainerInfoDTO> data) {}

    @Operation(summary = "사용 신청 승인", description = "PENDING 상태의 신청을 승인하고 우분투 계정을 생성합니다.")
    @ApiResponse(responseCode = "200", description = "성공")
    @ApiResponse(responseCode = "404", description = "신청 또는 리소스를 찾을 수 없음",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "우분투 계정명 중복",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    ResponseEntity<SuccessResponse<?>> approveRequest(ApproveRequestDTO dto);

    @Operation(summary = "사용 신청 거절", description = "PENDING 또는 FULFILLED 상태의 신청을 거절 처리합니다.")
    @ApiResponse(responseCode = "200", description = "성공")
    @ApiResponse(responseCode = "404", description = "신청을 찾을 수 없음",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "400", description = "이미 거절/삭제된 상태",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    ResponseEntity<SuccessResponse<?>> rejectRequest(RejectRequestDTO dto);
}