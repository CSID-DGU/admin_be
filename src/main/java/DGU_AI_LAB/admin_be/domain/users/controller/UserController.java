package DGU_AI_LAB.admin_be.domain.users.controller;

import DGU_AI_LAB.admin_be.domain.users.controller.docs.UserApi;
import DGU_AI_LAB.admin_be.domain.users.dto.request.PhoneUpdateRequestDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.request.ContactEmailUpdateRequestDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.request.UbuntuUsernameRegisterRequestDTO;
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

    /**
     * 내 Slack 가입 확인 결과 API — 신청하기 전에 화면이 미리 알려 주는 데 쓴다.
     * GET /api/users/me/slack-membership
     */
    @GetMapping("/me/slack-membership")
    public ResponseEntity<SuccessResponse<?>> getSlackMembership(@AuthenticationPrincipal CustomUserDetails principal) {
        return SuccessResponse.ok(userService.getSlackMembership(principal.getUserId()));
    }

    /**
     * 자주 사용하는 이메일 변경 API
     * PATCH /api/users/me/contact-email
     */
    @PatchMapping("/me/contact-email")
    public ResponseEntity<SuccessResponse<?>> updateContactEmail(
            @AuthenticationPrincipal CustomUserDetails principal,
            @RequestBody @Valid ContactEmailUpdateRequestDTO request
    ) {
        return SuccessResponse.ok(userService.updateContactEmail(principal.getUserId(), request));
    }

    /**
     * 우분투 유저네임 등록 API (가입 시 못 받은 기존 계정용, 1회성)
     * PATCH /api/users/me/ubuntu-username
     */
    @PatchMapping("/me/ubuntu-username")
    public ResponseEntity<SuccessResponse<?>> registerUbuntuUsername(
            @AuthenticationPrincipal CustomUserDetails principal,
            @RequestBody @Valid UbuntuUsernameRegisterRequestDTO request
    ) {
        UserResponseDTO updatedUser = userService.registerUbuntuUsername(principal.getUserId(), request);
        return SuccessResponse.ok(updatedUser);
    }
}
