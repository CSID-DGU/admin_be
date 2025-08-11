package DGU_AI_LAB.admin_be.domain.requests.controller;

import DGU_AI_LAB.admin_be.domain.requests.dto.request.ApproveModificationDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.ContainerInfoDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.ResourceUsageDTO;
import DGU_AI_LAB.admin_be.domain.requests.service.RequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/requests")
public class AdminRequestController {

    private final RequestService requestService;

    @PostMapping("/modify/approve")
    public ResponseEntity<Void> approveModification(@RequestBody ApproveModificationDTO dto) {
        requestService.approveModification(dto);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/usage")
    public ResponseEntity<List<ResourceUsageDTO>> getAllResourceUsage() {
        return ResponseEntity.ok(requestService.getAllFulfilledResourceUsage());
    }

    @GetMapping("/containers")
    public ResponseEntity<List<ContainerInfoDTO>> getAllActiveContainers() {
        return ResponseEntity.ok(requestService.getActiveContainers());
    }
}
