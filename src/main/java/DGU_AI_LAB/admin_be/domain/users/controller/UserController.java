package DGU_AI_LAB.admin_be.domain.users.controller;

import DGU_AI_LAB.admin_be.domain.users.controller.docs.UserApi;
import DGU_AI_LAB.admin_be.domain.users.dto.request.PasswordUpdateRequestDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.request.PhoneUpdateRequestDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.response.UserResponseDTO;
import DGU_AI_LAB.admin_be.domain.users.service.UserService;
import DGU_AI_LAB.admin_be.global.auth.CustomUserDetails;
import DGU_AI_LAB.admin_be.global.common.SuccessResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserController implements UserApi {

    private final UserService userService;

    /**
     * 사용자 정보 확인 API
     * @param principal
     * @return
     */
    @GetMapping("/me")
    public ResponseEntity<SuccessResponse<?>> getMyInfo(
            @AuthenticationPrincipal CustomUserDetails principal
    ) {
        return SuccessResponse.ok(userService.getMyInfo(principal.getUserId()));
    }

    /**
     * 사용자 비밀번호 변경 API
     * PATCH /api/users/me/password
     */
    @PatchMapping("/me/password")
    public ResponseEntity<SuccessResponse<?>> updateUserPassword(@AuthenticationPrincipal CustomUserDetails principal,
                                                                 @RequestBody @Valid PasswordUpdateRequestDTO request
    ) {
        UserResponseDTO updatedUser = userService.updatePassword(principal.getUserId(), request);
        return SuccessResponse.ok(updatedUser);
    }

    /**
     * 사용자 연락처 변경 API
     * PATCH /api/users/me/phone
     */
    @PatchMapping("/me/phone")
    public ResponseEntity<SuccessResponse<?>> updateUserPhone(@AuthenticationPrincipal CustomUserDetails principal,
                                                              @RequestBody @Valid PhoneUpdateRequestDTO request
    ) {
        UserResponseDTO updatedUser = userService.updatePhone(principal.getUserId(), request);
        return SuccessResponse.ok(updatedUser);
    }
}