package DGU_AI_LAB.admin_be.domain.groups.controller;

import DGU_AI_LAB.admin_be.domain.groups.dto.response.GroupLeaveResponseDTO;
import DGU_AI_LAB.admin_be.domain.groups.service.GroupLeaveService;
import DGU_AI_LAB.admin_be.global.auth.CustomUserDetails;
import DGU_AI_LAB.admin_be.global.common.SuccessResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** 관리자가 그룹 탈퇴 신청을 승인·거절한다. 승인하면 그룹 제거 작업이 등록된다. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/group-leave-requests")
public class AdminGroupLeaveController {

    private final GroupLeaveService groupLeaveService;

    /** GET /api/admin/group-leave-requests — 승인을 기다리는 신청. */
    @GetMapping
    public ResponseEntity<SuccessResponse<?>> pending() {
        return SuccessResponse.ok(groupLeaveService.pending().stream().map(GroupLeaveResponseDTO::fromEntity).toList());
    }

    @PostMapping("/{leaveRequestId}/approval")
    public ResponseEntity<SuccessResponse<?>> approve(@PathVariable Long leaveRequestId,
                                                      @AuthenticationPrincipal CustomUserDetails principal) {
        return SuccessResponse.accepted(GroupLeaveResponseDTO.fromEntity(
                groupLeaveService.approve(leaveRequestId, principal.getUserId())));
    }

    @PostMapping("/{leaveRequestId}/rejection")
    public ResponseEntity<SuccessResponse<?>> reject(@PathVariable Long leaveRequestId,
                                                     @AuthenticationPrincipal CustomUserDetails principal) {
        return SuccessResponse.ok(GroupLeaveResponseDTO.fromEntity(
                groupLeaveService.reject(leaveRequestId, principal.getUserId())));
    }
}
