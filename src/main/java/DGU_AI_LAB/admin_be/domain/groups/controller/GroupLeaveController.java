package DGU_AI_LAB.admin_be.domain.groups.controller;

import DGU_AI_LAB.admin_be.domain.groups.dto.response.GroupLeaveResponseDTO;
import DGU_AI_LAB.admin_be.domain.groups.service.GroupLeaveService;
import DGU_AI_LAB.admin_be.global.auth.CustomUserDetails;
import DGU_AI_LAB.admin_be.global.common.SuccessResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** 사용자가 그룹 탈퇴를 신청하고 자기 신청을 본다. 승인·거절은 {@link AdminGroupLeaveController}. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/groups")
public class GroupLeaveController {

    private final GroupLeaveService groupLeaveService;

    /** POST /api/groups/{groupId}/leave-requests — 속한 그룹의 탈퇴를 신청한다(승인 대기). */
    @PostMapping("/{groupId}/leave-requests")
    public ResponseEntity<SuccessResponse<?>> request(@PathVariable Long groupId,
                                                      @AuthenticationPrincipal CustomUserDetails principal) {
        return SuccessResponse.created(GroupLeaveResponseDTO.fromEntity(
                groupLeaveService.request(principal.getUserId(), groupId)));
    }

    /** GET /api/groups/leave-requests/me — 내 탈퇴 신청 내역. */
    @GetMapping("/leave-requests/me")
    public ResponseEntity<SuccessResponse<?>> mine(@AuthenticationPrincipal CustomUserDetails principal) {
        return SuccessResponse.ok(groupLeaveService.mine(principal.getUserId()).stream()
                .map(GroupLeaveResponseDTO::fromEntity).toList());
    }
}
