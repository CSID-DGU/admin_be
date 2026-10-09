package DGU_AI_LAB.admin_be.domain.requests.controller;

import DGU_AI_LAB.admin_be.domain.requests.controller.docs.AdminRequestChangeApi;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.ChangeDecisionRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.ChangeRequestResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.service.ChangeRequestDecisionService;
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

    private final ChangeRequestDecisionService changeRequestDecisionService;
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
        Status status = changeRequestDecisionService.approve(adminId, changeRequestId, dto.adminComment());
        // 반영 작업만 등록하고 돌아온 승인은 아직 끝나지 않았다 — 결과는 목록을 다시 읽어 확인한다.
        return status == Status.PROCESSING ? SuccessResponse.accepted(null) : SuccessResponse.ok(null);
    }

    @PostMapping("/{changeRequestId}/rejection")
    public ResponseEntity<SuccessResponse<?>> rejectModification(
            @AuthenticationPrincipal(expression = "userId") Long adminId,
            @PathVariable Long changeRequestId,
            @RequestBody @Valid ChangeDecisionRequestDTO dto
    ) {
        changeRequestDecisionService.reject(adminId, changeRequestId, dto.adminComment());
        return SuccessResponse.ok(null);
    }
}
