package DGU_AI_LAB.admin_be.domain.users.controller;

import DGU_AI_LAB.admin_be.domain.users.controller.docs.AdminPasswordResetApi;
import DGU_AI_LAB.admin_be.domain.users.service.PasswordResetService;
import DGU_AI_LAB.admin_be.global.common.SuccessResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/password-resets")
public class AdminPasswordResetController implements AdminPasswordResetApi {

    private final PasswordResetService passwordResetService;

    @GetMapping
    public ResponseEntity<SuccessResponse<?>> getOpenRequests() {
        return SuccessResponse.ok(passwordResetService.getOpenRequests());
    }

    @PostMapping("/{passwordResetRequestId}/approval")
    public ResponseEntity<SuccessResponse<?>> approve(@AuthenticationPrincipal(expression = "userId") Long adminId,
                                                      @PathVariable Long passwordResetRequestId) {
        return SuccessResponse.accepted(passwordResetService.approve(passwordResetRequestId, adminId));
    }

    @PostMapping("/{passwordResetRequestId}/rejection")
    public ResponseEntity<SuccessResponse<?>> deny(@AuthenticationPrincipal(expression = "userId") Long adminId,
                                                   @PathVariable Long passwordResetRequestId) {
        return SuccessResponse.ok(passwordResetService.deny(passwordResetRequestId, adminId));
    }
}
