package DGU_AI_LAB.admin_be.domain.requests.controller;

import DGU_AI_LAB.admin_be.domain.requests.dto.request.ChangeDecisionRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.ApproveModificationDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.RejectModificationDTO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;

import DGU_AI_LAB.admin_be.domain.requests.controller.docs.AdminRequestChangeApi;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.ApproveModificationDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.RejectModificationDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.ChangeRequestResponseDTO;
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
@RequestMapping("/api/admin/change-requests")
public class AdminRequestChangeController implements AdminRequestChangeApi {

    private final AdminRequestCommandService adminRequestCommandService;
    private final AdminRequestQueryService adminRequestQueryService;

    /**
     * 모든 변경 요청 목록 조회 (관리자용)
     * 모든 상태의 ChangeRequest 목록을 반환합니다.
     */
    @GetMapping
    public ResponseEntity<SuccessResponse<?>> getAllChangeRequests() {
        List<ChangeRequestResponseDTO> changeRequests = adminRequestQueryService.getAllChangeRequests();
        return SuccessResponse.ok(changeRequests);
    }

    @PostMapping("/{changeRequestId}/approval")
    public ResponseEntity<SuccessResponse<?>> approveModification(
            @AuthenticationPrincipal(expression = "userId") Long adminId,
            @PathVariable Long changeRequestId,
            @RequestBody @Valid ChangeDecisionRequestDTO dto
    ) {
        adminRequestCommandService.approveModification(adminId, new ApproveModificationDTO(changeRequestId, dto.adminComment()));
        return SuccessResponse.ok(null);
    }

    @PostMapping("/{changeRequestId}/rejection")
    public ResponseEntity<SuccessResponse<?>> rejectModification(
            @AuthenticationPrincipal(expression = "userId") Long adminId,
            @PathVariable Long changeRequestId,
            @RequestBody @Valid ChangeDecisionRequestDTO dto
    ) {
        adminRequestCommandService.rejectModification(adminId, new RejectModificationDTO(changeRequestId, dto.adminComment()));
        return SuccessResponse.ok(null);
    }
}
