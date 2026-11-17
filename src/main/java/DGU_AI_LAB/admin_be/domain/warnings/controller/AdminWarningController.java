package DGU_AI_LAB.admin_be.domain.warnings.controller;

import DGU_AI_LAB.admin_be.domain.adminActionLog.AdminAction;
import DGU_AI_LAB.admin_be.domain.adminActionLog.AdminActionType;
import DGU_AI_LAB.admin_be.domain.warnings.controller.docs.AdminWarningApi;
import DGU_AI_LAB.admin_be.domain.warnings.dto.WarningReasonRequestDTO;
import DGU_AI_LAB.admin_be.domain.warnings.service.WarningService;
import DGU_AI_LAB.admin_be.global.common.SuccessResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/users/{userId}")
public class AdminWarningController implements AdminWarningApi {

    private final WarningService warningService;

    @GetMapping("/warnings")
    public ResponseEntity<SuccessResponse<?>> getWarnings(@PathVariable Long userId) {
        return SuccessResponse.ok(warningService.getStatus(userId));
    }

    @PostMapping("/warnings")
    @AdminAction(value = AdminActionType.WARNING_GRANT, targetId = "userId")
    public ResponseEntity<SuccessResponse<?>> grantWarning(@PathVariable Long userId,
                                                           @AuthenticationPrincipal(expression = "userId") Long adminId,
                                                           @RequestBody @Valid WarningReasonRequestDTO dto) {
        return SuccessResponse.created(warningService.grant(adminId, userId, dto.reason()));
    }

    @PostMapping("/warning-deductions")
    @AdminAction(value = AdminActionType.WARNING_DEDUCT, targetId = "userId")
    public ResponseEntity<SuccessResponse<?>> deductWarning(@PathVariable Long userId,
                                                            @AuthenticationPrincipal(expression = "userId") Long adminId,
                                                            @RequestBody @Valid WarningReasonRequestDTO dto) {
        return SuccessResponse.created(warningService.deduct(adminId, userId, dto.reason()));
    }

    /** 사유는 본문이 아니라 쿼리(reason)로 받는다 — DELETE 본문은 중간 프록시가 버릴 수 있다. */
    @DeleteMapping("/warnings/{warningId}")
    @AdminAction(value = AdminActionType.WARNING_CANCEL, targetId = "userId")
    public ResponseEntity<SuccessResponse<?>> cancelWarning(@PathVariable Long userId, @PathVariable Long warningId,
                                                            @AuthenticationPrincipal(expression = "userId") Long adminId,
                                                            @ModelAttribute @Valid WarningReasonRequestDTO dto) {
        return SuccessResponse.ok(warningService.cancel(adminId, userId, warningId, dto.reason()));
    }
}
