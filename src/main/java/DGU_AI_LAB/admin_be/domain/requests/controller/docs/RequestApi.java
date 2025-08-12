package DGU_AI_LAB.admin_be.domain.requests.controller.docs;

import DGU_AI_LAB.admin_be.domain.requests.dto.request.SaveRequestRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.SaveRequestResponseDTO;
import DGU_AI_LAB.admin_be.global.auth.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;

import java.util.List;

@Tag(name = "서버 사용 신청", description = "서버 사용 신청 API")
public interface RequestApi {

    @Operation(
            summary = "서버 사용 신청 생성",
            description = "로그인된 사용자의 인증 정보를 바탕으로 서버 사용 신청을 생성합니다."
    )
    @ApiResponse(
            responseCode = "200",
            description = "신청 생성 성공",
            content = @Content(schema = @Schema(implementation = SaveRequestResponseDTO.class))
    )
    ResponseEntity<SaveRequestResponseDTO> createRequest(
            @Parameter(hidden = true, description = "인증된 사용자 ID")
            Long userId,
            @RequestBody(description = "서버 사용 신청 DTO", required = true)
            @Valid SaveRequestRequestDTO dto
    );

    @Operation(
            summary = "내 신청 목록 조회",
            description = "로그인된 사용자의 모든 신청 내역을 조회합니다."
    )
    @ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = SaveRequestResponseDTO.class)))
    )
    ResponseEntity<List<SaveRequestResponseDTO>> getMyRequests(
            @Parameter(hidden = true, description = "인증된 사용자")
            CustomUserDetails user
    );
}