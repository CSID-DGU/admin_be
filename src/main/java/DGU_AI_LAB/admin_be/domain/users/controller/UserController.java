package DGU_AI_LAB.admin_be.domain.users.controller;

import DGU_AI_LAB.admin_be.domain.users.service.UserService;
import DGU_AI_LAB.admin_be.global.auth.CustomUserDetails;
import DGU_AI_LAB.admin_be.global.common.SuccessResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<SuccessResponse<?>> getMyInfo(
            @AuthenticationPrincipal CustomUserDetails principal
    ) {
        return SuccessResponse.ok(userService.getMyInfo(principal.getUserId()));
    }
}

