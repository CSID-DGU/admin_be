package DGU_AI_LAB.admin_be.domain.requests.controller;

import DGU_AI_LAB.admin_be.domain.requests.controller.docs.AdminRequestApi;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.ApproveModificationDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.ApproveRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.RejectRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.ChangeRequestResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.ContainerInfoDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.ResourceUsageDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.SaveRequestResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.service.AdminRequestCommandService;
import DGU_AI_LAB.admin_be.domain.requests.service.AdminRequestQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/requests")
public class AdminRequestController implements AdminRequestApi {

    private final AdminRequestCommandService adminRequestCommandService;
    private final AdminRequestQueryService adminRequestQueryService;

    @PatchMapping("/change/approve")
    public ResponseEntity<Void> approveModification(
            @AuthenticationPrincipal(expression = "userId") Long adminId,
            @RequestBody @Valid ApproveModificationDTO dto
    ) {
        adminRequestCommandService.approveModification(adminId, dto);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/usage")
    public ResponseEntity<List<ResourceUsageDTO>> getAllResourceUsage() {
        return ResponseEntity.ok(adminRequestQueryService.getAllFulfilledResourceUsage());
    }

    @GetMapping("/containers")
    public ResponseEntity<List<ContainerInfoDTO>> getAllActiveContainers() {
        return ResponseEntity.ok(adminRequestQueryService.getAllActiveContainers());
    }

    /**
     * 모든 요청 목록 조회 (관리자용)
     * 모든 상태의 Request 목록을 반환합니다.
     */
    @GetMapping
    public ResponseEntity<List<SaveRequestResponseDTO>> getAllRequests() {
        return ResponseEntity.ok(adminRequestQueryService.getAllRequests());
    }

    /**
     * 신규 신청 목록 조회 (관리자용)
     * PENDING 상태의 Request 목록을 반환합니다.
     */
    @GetMapping("/new")
    public ResponseEntity<List<SaveRequestResponseDTO>> getNewRequests() {
        List<SaveRequestResponseDTO> requests = adminRequestQueryService.getNewRequests();
        return ResponseEntity.ok(requests);
    }

    /**
     * 변경 요청 목록 조회 (관리자용)
     * PENDING 상태의 ChangeRequest 목록을 반환합니다.
     */
    @GetMapping("/change")
    public ResponseEntity<List<ChangeRequestResponseDTO>> getChangeRequests() {
        List<ChangeRequestResponseDTO> changeRequests = adminRequestQueryService.getChangeRequests();
        return ResponseEntity.ok(changeRequests);
    }

    @PatchMapping("/approval")
    public ResponseEntity<SaveRequestResponseDTO> approve(@RequestBody @Valid ApproveRequestDTO dto) {
        return ResponseEntity.ok(adminRequestCommandService.approveRequest(dto));
    }

    @PatchMapping("/reject")
    public ResponseEntity<SaveRequestResponseDTO> reject(@RequestBody @Valid RejectRequestDTO dto) {
        return ResponseEntity.ok(adminRequestCommandService.rejectRequest(dto));
    }
}