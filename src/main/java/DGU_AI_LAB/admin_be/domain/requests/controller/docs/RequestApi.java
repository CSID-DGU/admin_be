package DGU_AI_LAB.admin_be.domain.requests.controller.docs;

import DGU_AI_LAB.admin_be.domain.requests.dto.request.SaveRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.RequestResponseDTO;
import DGU_AI_LAB.admin_be.global.common.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@Tag(name = "사용 신청", description = "서버 사용 신청 등록 및 조회 API")
public interface RequestApi {

    @Operation(summary = "서버 사용 신청 등록", description = "사용자가 서버 사용을 신청합니다.")
    @ApiResponse(responseCode = "200", description = "신청 성공",
            content = @Content(schema = @Schema(implementation = SuccessResponse.class)))
    ResponseEntity<?> saveRequest(@RequestBody @Valid SaveRequestDTO request);

    @Operation(summary = "전체 신청 목록 조회", description = "등록된 모든 신청 목록을 조회합니다.")
    @ApiResponse(responseCode = "200", description = "신청 목록 반환",
            content = @Content(schema = @Schema(implementation = SuccessResponse.class)))
    ResponseEntity<?> getAllRequests();

    @Operation(summary = "개별 신청 조회", description = "ID로 특정 신청 내용을 조회합니다.")
    @ApiResponse(responseCode = "200", description = "신청 반환",
            content = @Content(schema = @Schema(implementation = SaveRequestDTO.class)))
    ResponseEntity<RequestResponseDTO> getRequest(
            @Parameter(description = "신청 ID", example = "1") @PathVariable Long id
    );

    @Operation(summary = "사용자별 신청 조회", description = "UserID로 사용자별 신청 내용을 조회합니다.")
    @ApiResponse(responseCode = "200", description = "신청 반환",
            content = @Content(schema = @Schema(implementation = SaveRequestDTO.class)))
    ResponseEntity<List<RequestResponseDTO>> getRequestsByUser(
            @Parameter(description = "USERID", example = "1") @PathVariable Long userId
    );
}
