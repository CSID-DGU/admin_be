package DGU_AI_LAB.admin_be.domain.requests.controller.docs;

import DGU_AI_LAB.admin_be.domain.requests.dto.request.RestartPodRequestDTO;
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
                    "SSH(Ubuntu) 비밀번호는 웹 계정 비밀번호와 같아 따로 받지 않습니다. 리눅스용 해시가 아직 없는 " +
                    "세션(해시가 생기기 전에 로그인)이면 400 UBUNTU_PASSWORD_REQUIRED — 다시 로그인하면 채워집니다. " +
                    "배정 안내 메일에도 비밀번호는 들어가지 않습니다."
    )
    @ApiResponse(responseCode = "201", description = "신청 생성 성공",
            content = @Content(schema = @Schema(implementation = SaveRequestResponseDoc.class)))
    @ApiResponse(responseCode = "400", description = "우분투 계정명 중복 또는 유효하지 않은 요청",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "리소스 그룹 또는 컨테이너 이미지를 찾을 수 없음",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "429", description = "하루 신청 한도(5건) 초과",
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

    @Operation(summary = "내 컨테이너 재시작", description = "현재 노드에서 컨테이너를 다시 만드는 작업을 등록하고 바로 202로 돌아옵니다(신청은 MIGRATING). "
            + "keepChanges(기본 true)면 설치한 패키지 등 컨테이너 변경분을 유지하고, false면 기본 이미지로 초기화합니다. "
            + "홈 디렉터리는 유지되고 실행 중이던 프로세스는 이어지지 않습니다. 1시간에 5번까지 할 수 있습니다.")
    @ApiResponse(responseCode = "202", description = "작업 등록됨")
    @ApiResponse(responseCode = "403", description = "본인 소유의 신청이 아님",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "신청을 찾을 수 없음",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "FULFILLED 상태가 아니거나 이미 진행 중",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "429", description = "재시작 횟수 초과",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    ResponseEntity<SuccessResponse<?>> restartMyContainer(
            @Parameter(hidden = true) Long userId,
            @Parameter(description = "재시작할 신청 ID") Long requestId,
            RestartPodRequestDTO dto
    );

    @Operation(summary = "내 컨테이너의 마지막 재시작 결과", description = "마지막 재시작 작업의 phase와 결과를 조회합니다. 작업이 없으면 phase가 none입니다.")
    @ApiResponse(responseCode = "200", description = "성공")
    @ApiResponse(responseCode = "403", description = "본인 소유의 신청이 아님",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    ResponseEntity<SuccessResponse<?>> getMyLatestRestart(
            @Parameter(hidden = true) Long userId,
            @Parameter(description = "신청 ID") Long requestId
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

    @Operation(summary = "내 신청 목록 조회", description = "로그인된 사용자의 모든 신청 내역(전체 상태 포함)을 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공",
            content = @Content(schema = @Schema(implementation = SaveRequestListResponseDoc.class)))
    ResponseEntity<SuccessResponse<?>> getMyRequests(@Parameter(hidden = true) CustomUserDetails user);

    @Operation(summary = "내 승인 완료 신청 목록 조회", description = "FULFILLED 상태인 신청 목록만 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공",
            content = @Content(schema = @Schema(implementation = SaveRequestListResponseDoc.class)))
    ResponseEntity<SuccessResponse<?>> getMyApprovedRequests(@Parameter(hidden = true) CustomUserDetails user);

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