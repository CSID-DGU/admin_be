package DGU_AI_LAB.admin_be.domain.users.controller;

import DGU_AI_LAB.admin_be.domain.users.controller.docs.AuthApi;
import DGU_AI_LAB.admin_be.domain.users.dto.request.EmailSendRequestDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.request.PasswordResetRequestDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.request.UserLoginRequestDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.request.UserRegisterRequestDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.response.UserTokenResponseDTO;
import DGU_AI_LAB.admin_be.domain.users.service.SelfPasswordResetService;
import DGU_AI_LAB.admin_be.domain.users.service.UserLoginService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController implements AuthApi {

    private final UserLoginService userLoginService;
    private final SelfPasswordResetService selfPasswordResetService;

    /**
     * 3) 회원가입
     */
    @PostMapping("/register")
    public ResponseEntity<Void> register(@RequestBody @Valid UserRegisterRequestDTO request) {
        userLoginService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }


    /**
     * 로그인 (이메일 기반)
     */
    @PostMapping("/login")
    public ResponseEntity<UserTokenResponseDTO> login(@RequestBody @Valid UserLoginRequestDTO request) {
        return ResponseEntity.ok(userLoginService.login(request));
    }

    /**
     * 비밀번호 재설정 요청 — 가입된 메일이면 인증번호를 보낸다. 가입 여부와 무관하게 200.
     */
    @PostMapping("/password-resets")
    public ResponseEntity<Void> requestPasswordReset(@RequestBody @Valid EmailSendRequestDTO request) {
        selfPasswordResetService.requestCode(request.email());
        return ResponseEntity.ok().build();
    }

    /**
     * 메일로 받은 인증번호로 비밀번호 교체
     */
    @PutMapping("/password")
    public ResponseEntity<Void> resetPassword(@RequestBody @Valid PasswordResetRequestDTO request) {
        selfPasswordResetService.reset(request.email(), request.code(), request.newPassword());
        return ResponseEntity.noContent().build();
    }
}
