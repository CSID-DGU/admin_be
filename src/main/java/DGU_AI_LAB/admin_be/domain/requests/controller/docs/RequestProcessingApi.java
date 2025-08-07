package DGU_AI_LAB.admin_be.domain.requests.controller.docs;

import DGU_AI_LAB.admin_be.domain.approval.entity.Approval;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.RequestApproveDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.RequestRejectDTO;
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

@Tag(name = "신청 처리", description = "서버 사용 신청 승인 및 거절 처리 API")
public interface RequestProcessingApi {

    @Operation(summary = "사용 신청 승인", description = "사용자의 서버 사용 신청을 승인하고, 승인 정보를 저장합니다.")
    @ApiResponse(responseCode = "200", description = "승인 성공",
            content = @Content(schema = @Schema(implementation = SuccessResponse.class)))
    ResponseEntity<?> approve(@RequestBody @Valid RequestApproveDTO request);

    @Operation(summary = "승인 정보 조회", description = "승인된 신청의 상세 정보를 조회합니다.")
    @ApiResponse(responseCode = "200", description = "승인 정보 반환",
            content = @Content(schema = @Schema(implementation = Approval.class)))
    ResponseEntity<Approval> getApprovalInfo(
            @Parameter(description = "승인 ID", example = "1") @PathVariable Long id
    );

    @Operation(summary = "사용 신청 거절", description = "사용자의 신청을 거절 처리합니다.")
    @ApiResponse(responseCode = "200", description = "거절 처리 완료",
            content = @Content(schema = @Schema(implementation = RequestRejectDTO.class)))
    ResponseEntity<?> reject(@Parameter(description = "거절 ID", example = "1") @RequestBody @Valid RequestRejectDTO request);
}
