package DGU_AI_LAB.admin_be.domain.requests.controller;

import DGU_AI_LAB.admin_be.domain.requests.controller.docs.AdminRequestApi;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.ApproveRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.RejectRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.ContainerInfoDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.ResourceUsageDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.SaveRequestResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.service.RequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/requests")
public class AdminRequestController implements AdminRequestApi {

    private final RequestService requestService;

    /*@PostMapping("/modify/approve")
    public ResponseEntity<Void> approveModification(@RequestBody ApproveModificationDTO dto) {
        requestService.approveModification(dto);
        return ResponseEntity.ok().build();
    }*/

    @GetMapping("/usage")
    public ResponseEntity<List<ResourceUsageDTO>> getAllResourceUsage() {
        return ResponseEntity.ok(requestService.getAllFulfilledResourceUsage());
    }

    @GetMapping("/containers")
    public ResponseEntity<List<ContainerInfoDTO>> getAllActiveContainers() {
        return ResponseEntity.ok(requestService.getActiveContainers());
    }

    @GetMapping
    public ResponseEntity<List<SaveRequestResponseDTO>> getAllRequests() {
        List<SaveRequestResponseDTO> requests = requestService.getAllRequests();
        return ResponseEntity.ok(requests);
    }

    @PatchMapping("/approval")
    public ResponseEntity<SaveRequestResponseDTO> approve(@RequestBody @Valid ApproveRequestDTO dto) {
        return ResponseEntity.ok(requestService.approveRequest(dto));
    }

    @PatchMapping("/reject")
    public ResponseEntity<SaveRequestResponseDTO> reject(@RequestBody @Valid RejectRequestDTO dto) {
        return ResponseEntity.ok(requestService.rejectRequest(dto));
    }
}
