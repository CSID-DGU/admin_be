package DGU_AI_LAB.admin_be.domain.requests.controller.docs;

import DGU_AI_LAB.admin_be.domain.requests.dto.request.ApproveModificationDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.ApproveRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.RejectRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.ChangeRequestResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.ContainerInfoDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.ResourceUsageDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.SaveRequestResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "1. 관리자 서버 사용 신청 처리", description = "관리자용 서버 사용 신청 관리 API")
public interface AdminRequestApi {

    @Operation(summary = "변경 요청 승인", description = "사용자의 변경 요청을 승인합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "성공"),
            @ApiResponse(responseCode = "404", description = "변경 요청을 찾을 수 없음"),
            @ApiResponse(responseCode = "409", description = "요청 상태가 PENDING이 아님")
    })
    @PatchMapping("/change/approve")
    ResponseEntity<Void> approveModification(
            @AuthenticationPrincipal(expression = "userId") Long adminId,
            @RequestBody @Valid ApproveModificationDTO dto
    );

    @Operation(summary = "모든 리소스 사용량 조회", description = "현재 사용 중인 컨테이너들의 리소스 사용량을 조회합니다.")
    @GetMapping("/usage")
    ResponseEntity<List<ResourceUsageDTO>> getAllResourceUsage();

    @Operation(summary = "모든 컨테이너 정보 조회", description = "현재 활성화된 모든 컨테이너의 상세 정보를 조회합니다.")
    @GetMapping("/containers")
    ResponseEntity<List<ContainerInfoDTO>> getAllActiveContainers();

    @Operation(summary = "모든 요청 목록 조회", description = "모든 상태의 사용자 요청 목록을 조회합니다.")
    @GetMapping
    ResponseEntity<List<SaveRequestResponseDTO>> getAllRequests();

    @Operation(summary = "신규 신청 목록 조회", description = "PENDING 상태의 신규 사용자 신청 목록을 조회합니다.")
    @GetMapping("/new")
    ResponseEntity<List<SaveRequestResponseDTO>> getNewRequests();

    @Operation(summary = "변경 요청 목록 조회", description = "PENDING 상태의 사용자 변경 요청 목록을 조회합니다.")
    @GetMapping("/change")
    ResponseEntity<List<ChangeRequestResponseDTO>> getChangeRequests();

    @Operation(summary = "신규 신청 승인", description = "신규 사용자 신청을 승인하고, 계정 및 리소스를 할당합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "성공"),
            @ApiResponse(responseCode = "404", description = "요청을 찾을 수 없음"),
            @ApiResponse(responseCode = "409", description = "요청 상태가 PENDING이 아님"),
            @ApiResponse(responseCode = "502", description = "외부 서버 오류")
    })
    @PatchMapping("/approval")
    ResponseEntity<SaveRequestResponseDTO> approve(@RequestBody @Valid ApproveRequestDTO dto);

    @Operation(summary = "신규 신청 거절", description = "신규 사용자 신청을 거절합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "성공"),
            @ApiResponse(responseCode = "404", description = "요청을 찾을 수 없음"),
            @ApiResponse(responseCode = "409", description = "요청 상태가 PENDING 또는 FULFILLED가 아님")
    })
    @PatchMapping("/reject")
    ResponseEntity<SaveRequestResponseDTO> reject(@RequestBody @Valid RejectRequestDTO dto);
}