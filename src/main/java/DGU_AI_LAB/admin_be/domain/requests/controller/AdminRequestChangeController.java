package DGU_AI_LAB.admin_be.domain.requests.controller;

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
@RequestMapping("/api/admin/requests/change")
public class AdminRequestChangeController implements AdminRequestChangeApi {

    private final AdminRequestCommandService adminRequestCommandService;
    private final AdminRequestQueryService adminRequestQueryService;

    /**
     * 변경 요청 목록 조회 (관리자용)
     * PENDING 상태의 ChangeRequest 목록을 반환합니다.
     */
    @GetMapping
    public ResponseEntity<SuccessResponse<?>> getChangeRequests() {
        List<ChangeRequestResponseDTO> changeRequests = adminRequestQueryService.getChangeRequests();
        return ResponseEntity.ok((SuccessResponse<?>) changeRequests);
    }

    @PatchMapping("/approve")
    public ResponseEntity<SuccessResponse<?>> approveModification(
            @AuthenticationPrincipal(expression = "userId") Long adminId,
            @RequestBody @Valid ApproveModificationDTO dto
    ) {
        adminRequestCommandService.approveModification(adminId, dto);
        return ResponseEntity.ok().build();
    }


    @PatchMapping("/reject")
    public ResponseEntity<SuccessResponse<?>> rejectModification(
            @AuthenticationPrincipal(expression = "userId") Long adminId,
            @RequestBody @Valid RejectModificationDTO dto
    ) {
        adminRequestCommandService.rejectModification(adminId, dto);
        return ResponseEntity.ok().build();
    }

}
