package DGU_AI_LAB.admin_be.domain.requests.controller;

import DGU_AI_LAB.admin_be.domain.requests.controller.docs.AdminRequestApi;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.ApproveModificationDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.ApproveRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.RejectModificationDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.RejectRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.ChangeRequestResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.ContainerInfoDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.ResourceUsageDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.SaveRequestResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.service.AdminRequestCommandService;
import DGU_AI_LAB.admin_be.domain.requests.service.AdminRequestQueryService;
import DGU_AI_LAB.admin_be.global.common.SuccessResponse;
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


    /**
     * 모든 요청 목록 조회 (관리자용)
     * 모든 상태의 Request 목록을 반환합니다.
     */
    @GetMapping
    public ResponseEntity<SuccessResponse<?>> getAllRequests() {
        List<SaveRequestResponseDTO> requests = adminRequestQueryService.getAllRequests();
        return SuccessResponse.ok(requests);
    }

    /**
     * 신규 신청 목록 조회 (관리자용)
     * PENDING 상태의 Request 목록을 반환합니다.
     */
    @GetMapping("/new")
    public ResponseEntity<SuccessResponse<?>> getNewRequests() {
        List<SaveRequestResponseDTO> requests = adminRequestQueryService.getNewRequests();
        return SuccessResponse.ok(requests);
    }

    @GetMapping("/usage")
    public ResponseEntity<SuccessResponse<?>> getAllResourceUsage() {
        List<ResourceUsageDTO> usage = adminRequestQueryService.getAllFulfilledResourceUsage();
        return SuccessResponse.ok(usage);
    }

    @GetMapping("/containers")
    public ResponseEntity<SuccessResponse<?>> getAllActiveContainers() {
        List<ContainerInfoDTO> containers = adminRequestQueryService.getAllActiveContainers();
        return SuccessResponse.ok(containers);
    }


    @PatchMapping("/approve")
    public ResponseEntity<SuccessResponse<?>> approveRequest(@RequestBody @Valid ApproveRequestDTO dto) {
        SaveRequestResponseDTO responseDto = adminRequestCommandService.approveRequest(dto);
        return SuccessResponse.ok(responseDto);
    }

    @PatchMapping("/reject")
    public ResponseEntity<SuccessResponse<?>> rejectRequest(@RequestBody @Valid RejectRequestDTO dto) {
        SaveRequestResponseDTO responseDto = adminRequestCommandService.rejectRequest(dto);
        return SuccessResponse.ok(responseDto);
    }
}
