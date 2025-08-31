package DGU_AI_LAB.admin_be.domain.requests.controller.docs;

import DGU_AI_LAB.admin_be.domain.requests.dto.response.AcceptInfoResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "Config Server용 승인 정보 관리", description = "Ubuntu username별 승인 정보 조회 API")
public interface AcceptInfoApi {

    @Operation(
            summary = "사용자 승인 정보 조회",
            description = "Ubuntu username으로 승인된 서버 사용 신청 정보를 조회합니다."
    )
    @ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = @Content(schema = @Schema(implementation = AcceptInfoResponseDTO.class))
    )
    @ApiResponse(
            responseCode = "404",
            description = "해당 username의 승인 정보를 찾을 수 없음",
            content = @Content
    )
    ResponseEntity<AcceptInfoResponseDTO> getAcceptInfo(
            @Parameter(description = "Ubuntu username")
            String username
    );
}