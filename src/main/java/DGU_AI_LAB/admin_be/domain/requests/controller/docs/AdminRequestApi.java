package DGU_AI_LAB.admin_be.domain.requests.controller.docs;

import DGU_AI_LAB.admin_be.domain.requests.dto.request.ApproveRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.RejectRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.SaveRequestResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

import java.util.List;

@Tag(name = "1. 관리자용 서버 사용 신청 관리", description = "관리자용 서버 사용 신청 관리 API")
public interface AdminRequestApi {

    @Operation(
            summary = "모든 신청 목록 조회",
            description = "상태에 상관없이 모든 사용 신청을 조회합니다."
    )
    @ApiResponse(
            responseCode = "200", description = "조회 성공",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = SaveRequestResponseDTO.class)))
    )
    ResponseEntity<List<SaveRequestResponseDTO>> getAllRequests();

    @Operation(
            summary = "신청 승인",
            description = "해당 신청의 상태를 FULFILLED로 변경하고 approvedAt을 현재 시각으로 설정합니다. expiresAt, volumeSizeGiB, imageId, rsgroupId를 갱신합니다."
    )
    @ApiResponse(
            responseCode = "200", description = "승인 성공",
            content = @Content(schema = @Schema(implementation = SaveRequestResponseDTO.class))
    )
    ResponseEntity<SaveRequestResponseDTO> approve(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "승인 DTO", required = true
            )
            ApproveRequestDTO dto
    );

    @Operation(
            summary = "신청 거절",
            description = "해당 신청의 상태를 DENIED로 변경하고 관리 코멘트를 기록합니다."
    )
    @ApiResponse(
            responseCode = "200", description = "거절 성공",
            content = @Content(schema = @Schema(implementation = SaveRequestResponseDTO.class))
    )
    ResponseEntity<SaveRequestResponseDTO> reject(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "거절 DTO", required = true
            )
            RejectRequestDTO dto
    );
}