package DGU_AI_LAB.admin_be.domain.users.controller;

import DGU_AI_LAB.admin_be.domain.users.controller.docs.EmailApi;
import DGU_AI_LAB.admin_be.domain.users.dto.request.EmailSendRequestDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.request.EmailVerifyRequestDTO;
import DGU_AI_LAB.admin_be.global.common.SuccessCode;
import DGU_AI_LAB.admin_be.global.common.SuccessResponse;
import DGU_AI_LAB.admin_be.global.util.EmailService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/email")
@RequiredArgsConstructor
public class EmailController implements EmailApi {

    private final EmailService emailService;

    // 1) 이메일 인증번호 전송
    @PostMapping("/send")
    public ResponseEntity<SuccessResponse<?>> sendCode(@RequestBody @Valid EmailSendRequestDTO request) {
        emailService.sendEmailVerificationCode(request.email());
        return SuccessResponse.ok(SuccessCode.EMAIL_SENT.getMessage());
    }

    // 2) 인증번호 확인 및 인증 상태 저장 -> AuthController의 /register로 이동
    @PostMapping("/verify")
    public ResponseEntity<SuccessResponse<?>> verifyCode(@RequestBody @Valid EmailVerifyRequestDTO request) {
        emailService.confirmAuthCode(request.email(), request.code());
        return SuccessResponse.ok(SuccessCode.EMAIL_VERIFIED.getMessage());
    }
}
