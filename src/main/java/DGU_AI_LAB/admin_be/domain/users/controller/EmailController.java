package DGU_AI_LAB.admin_be.domain.users.controller;

import DGU_AI_LAB.admin_be.domain.users.controller.docs.EmailApi;
import DGU_AI_LAB.admin_be.global.common.SuccessCode;
import DGU_AI_LAB.admin_be.global.common.SuccessResponse;
import DGU_AI_LAB.admin_be.global.util.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth/email")
@RequiredArgsConstructor
public class EmailController implements EmailApi {

    private final EmailService emailService;

    // 1) 이메일 인증번호 전송
    @PostMapping("/send")
    public ResponseEntity<SuccessResponse<?>> sendCode(@RequestParam String email) {
        emailService.sendEmailVerificationCode(email);
        return SuccessResponse.ok(SuccessCode.EMAIL_SENT.getMessage());
    }

    // 2) 인증번호 확인 및 인증 상태 저장 -> AuthController의 /register로 이동
    @PostMapping("/verify")
    public ResponseEntity<SuccessResponse<?>> verifyCode(@RequestParam String email, @RequestParam String code) {
        emailService.confirmAuthCode(email, code);
        return SuccessResponse.ok(SuccessCode.EMAIL_VERIFIED.getMessage());
    }
}
