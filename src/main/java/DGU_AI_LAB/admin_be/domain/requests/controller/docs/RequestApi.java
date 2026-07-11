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
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@Tag(name = "2. 사용자 서버 신청", description = "서버 사용 신청 생성 및 조회 API")
public interface RequestApi {

    @Operation(
            summary = "서버 사용 신청 생성",
            description = "로그인된 사용자의 서버 사용 신청을 생성합니다. " +
                    "ubuntuPassword는 클라이언트에서 평문을 Base64로 인코딩한 값으로 전송합니다."
    )
    @ApiResponse(responseCode = "201", description = "신청 생성 성공",
            content = @Content(schema = @Schema(implementation = SaveRequestResponseDTO.class)))
    @ApiResponse(responseCode = "400", description = "우분투 계정명 중복 또는 유효하지 않은 요청",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "리소스 그룹 또는 컨테이너 이미지를 찾을 수 없음",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    ResponseEntity<SuccessResponse<?>> createRequest(
            @Parameter(hidden = true) Long userId,
            @Valid SaveRequestRequestDTO dto
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
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = SaveRequestResponseDTO.class))))
    ResponseEntity<SuccessResponse<?>> getMyRequests(@Parameter(hidden = true) CustomUserDetails user);

    @Operation(summary = "내 승인 완료 신청 목록 조회", description = "FULFILLED 상태인 신청 목록만 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = SaveRequestResponseDTO.class))))
    ResponseEntity<SuccessResponse<?>> getMyApprovedRequests(@Parameter(hidden = true) CustomUserDetails user);

    @Operation(summary = "승인된 우분투 계정명 목록 조회", description = "FULFILLED 상태인 모든 신청의 ubuntuUsername 목록을 조회합니다. 그룹 생성 시 멤버 선택에 활용합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    ResponseEntity<SuccessResponse<?>> getAllFulfilledUsernames();

    @Operation(summary = "내 변경 요청 목록 조회", description = "로그인된 사용자가 제출한 모든 변경 요청 내역(전체 상태 포함)을 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = ChangeRequestResponseDTO.class))))
    ResponseEntity<SuccessResponse<?>> getMyChangeRequests(@Parameter(hidden = true) CustomUserDetails user);
}