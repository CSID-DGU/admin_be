package DGU_AI_LAB.admin_be.domain.requests.controller;

import DGU_AI_LAB.admin_be.domain.requests.controller.docs.AdminRequestApi;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.ApproveModificationDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.ApproveRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.RejectRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.ContainerInfoDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.ResourceUsageDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.SaveRequestResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.service.AdminRequestService;
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

    @GetMapping("/usage")
    public ResponseEntity<List<ResourceUsageDTO>> getAllResourceUsage() {
        return ResponseEntity.ok(adminRequestService.getAllFulfilledResourceUsage());
    }

    @GetMapping("/containers")
    public ResponseEntity<List<ContainerInfoDTO>> getAllActiveContainers() {
        return ResponseEntity.ok(adminRequestService.getAllActiveContainers());
    }

    @GetMapping
    public ResponseEntity<List<SaveRequestResponseDTO>> getAllRequests() {
        List<SaveRequestResponseDTO> requests = adminRequestService.getAllRequests();
        return ResponseEntity.ok(requests);
    }

    private final AdminRequestService adminRequestService;

    @PatchMapping("/change/approve")
    public ResponseEntity<Void> approveModification(
            @AuthenticationPrincipal(expression = "userId") Long adminId,
            @RequestBody @Valid ApproveModificationDTO dto
    ) {
        adminRequestService.approveModification(adminId, dto);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/approval")
    public ResponseEntity<SaveRequestResponseDTO> approve(@RequestBody @Valid ApproveRequestDTO dto) {
        return ResponseEntity.ok(adminRequestService.approveRequest(dto));
    }

    @PatchMapping("/reject")
    public ResponseEntity<SaveRequestResponseDTO> reject(@RequestBody @Valid RejectRequestDTO dto) {
        return ResponseEntity.ok(adminRequestService.rejectRequest(dto));
    }
}