package DGU_AI_LAB.admin_be.domain.requests.controller.docs;

import DGU_AI_LAB.admin_be.domain.requests.dto.request.SaveRequestRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.SingleChangeRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.ChangeRequestResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.SaveRequestResponseDTO;
import DGU_AI_LAB.admin_be.error.dto.ErrorResponse;
import DGU_AI_LAB.admin_be.global.auth.CustomUserDetails;
import DGU_AI_LAB.admin_be.global.common.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.List;

@Tag(name = "2. 사용자 서버 신청", description = "서버 사용 신청 생성 및 조회 API")
public interface RequestApi {

    @Operation(
            summary = "서버 사용 신청 생성",
            description = "로그인된 사용자의 서버 사용 신청을 생성합니다. " +
                    "ubuntuPassword는 클라이언트에서 평문을 Base64로 인코딩한 값으로 전송합니다."
    )
    @ApiResponse(responseCode = "201", description = "신청 생성 성공",
            content = @Content(schema = @Schema(implementation = SaveRequestResponseDoc.class)))
    @ApiResponse(responseCode = "400", description = "우분투 계정명 중복 또는 유효하지 않은 요청",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "리소스 그룹 또는 컨테이너 이미지를 찾을 수 없음",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    ResponseEntity<SuccessResponse<?>> createRequest(
            @Parameter(hidden = true) Long userId,
            @Valid SaveRequestRequestDTO dto
    );

    @Operation(summary = "내 신청 취소", description = "PENDING 또는 DENIED 상태인 나의 신청을 취소(삭제)합니다. 이미 승인되어 컨테이너가 떠 있는 신청은 취소할 수 없습니다.")
    @ApiResponse(responseCode = "200", description = "취소 성공")
    @ApiResponse(responseCode = "400", description = "취소할 수 없는 상태(FULFILLED/MIGRATING 등)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "본인 소유의 신청이 아님",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "신청을 찾을 수 없음",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @DeleteMapping("/{requestId}")
    ResponseEntity<SuccessResponse<?>> cancelRequest(
            @Parameter(hidden = true) Long userId,
            @PathVariable @Parameter(description = "취소할 신청 ID") Long requestId
    );

    @Operation(summary = "서버 설정 단건 변경 요청 생성", description = "승인된 신청에 대해 볼륨 크기, 만료 기한 등 단일 항목 변경을 요청합니다.")
    @ApiResponse(responseCode = "200", description = "변경 요청 생성 성공")
    @ApiResponse(responseCode = "400", description = "FULFILLED 상태가 아닌 신청 또는 소유자 불일치",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "신청을 찾을 수 없음",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/{requestId}/change")
    ResponseEntity<SuccessResponse<?>> createChangeRequest(
            @Parameter(hidden = true) Long userId,
            @PathVariable @Parameter(description = "변경 대상 신청 ID") Long requestId,
            @Valid SingleChangeRequestDTO dto
    );

    @Operation(
            summary = "내 컨테이너 재시작",
            description = "FULFILLED 상태인 나의 컨테이너를 같은 노드에서 재시작합니다. 새 컨테이너가 정상 확인된 뒤에야 " +
                    "기존 컨테이너가 정리되므로, 실패하더라도 기존 컨테이너는 그대로 유지됩니다. " +
                    "즉시 status=REBOOTING인 신청 정보를 반환하며, 실제 완료 여부는 '내 승인 완료 신청 목록 조회'를 " +
                    "폴링해 status가 FULFILLED로 돌아오는지로 확인합니다."
    )
    @ApiResponse(responseCode = "200", description = "재시작 접수 성공 (status=REBOOTING)",
            content = @Content(schema = @Schema(implementation = SaveRequestResponseDoc.class)))
    @ApiResponse(responseCode = "400", description = "본인 소유의 신청이 아님",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "신청을 찾을 수 없음",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "FULFILLED 상태가 아니거나(이미 재시작/마이그레이션 진행 중) 배치된 노드 정보가 없음",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "429", description = "동시에 처리 중인 재시작 요청이 많음",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/{requestId}/reboot")
    ResponseEntity<SuccessResponse<?>> rebootPod(
            @Parameter(hidden = true) Long userId,
            @PathVariable @Parameter(description = "재시작할 신청 ID") Long requestId
    );

    @Operation(summary = "내 신청 목록 조회", description = "로그인된 사용자의 모든 신청 내역(전체 상태 포함)을 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공",
            content = @Content(schema = @Schema(implementation = SaveRequestListResponseDoc.class)))
    ResponseEntity<SuccessResponse<?>> getMyRequests(@Parameter(hidden = true) CustomUserDetails user);

    @Operation(summary = "내 승인 완료 신청 목록 조회",
            description = "컨테이너가 살아있는 신청 목록(FULFILLED, 마이그레이션 중 MIGRATING, 재시작 중 REBOOTING)을 조회합니다. " +
                    "각 항목의 status로 재시작 진행 상태를 폴링할 수 있습니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공",
            content = @Content(schema = @Schema(implementation = SaveRequestListResponseDoc.class)))
    ResponseEntity<SuccessResponse<?>> getMyApprovedRequests(@Parameter(hidden = true) CustomUserDetails user);

    @Operation(summary = "승인된 우분투 계정명 목록 조회", description = "FULFILLED 상태인 모든 신청의 ubuntuUsername 목록을 조회합니다. 그룹 생성 시 멤버 선택에 활용합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공",
            content = @Content(schema = @Schema(implementation = FulfilledUsernameListResponseDoc.class)))
    ResponseEntity<SuccessResponse<?>> getAllFulfilledUsernames();

    @Operation(summary = "내 변경 요청 목록 조회", description = "로그인된 사용자가 제출한 모든 변경 요청 내역(전체 상태 포함)을 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공",
            content = @Content(schema = @Schema(implementation = ChangeRequestListResponseDoc.class)))
    ResponseEntity<SuccessResponse<?>> getMyChangeRequests(@Parameter(hidden = true) CustomUserDetails user);

    // 실제 응답은 SuccessResponse<T> 래퍼로 감싸져 나가므로, Swagger 스키마도 래퍼 형태로 노출한다.
    @Schema(name = "SuccessResponseSaveRequestResponseDTO", description = "서버 사용 신청 생성 응답")
    record SaveRequestResponseDoc(int status, String message, SaveRequestResponseDTO data) {}

    @Schema(name = "SuccessResponseListSaveRequestResponseDTO", description = "서버 사용 신청 목록 응답")
    record SaveRequestListResponseDoc(int status, String message, List<SaveRequestResponseDTO> data) {}

    @Schema(name = "SuccessResponseListChangeRequestResponseDTO", description = "변경 요청 목록 응답")
    record ChangeRequestListResponseDoc(int status, String message, List<ChangeRequestResponseDTO> data) {}

    @Schema(name = "SuccessResponseListString", description = "승인된 우분투 계정명 목록 응답")
    record FulfilledUsernameListResponseDoc(int status, String message, List<String> data) {}
}