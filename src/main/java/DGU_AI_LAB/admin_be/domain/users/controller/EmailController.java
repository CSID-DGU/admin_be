package DGU_AI_LAB.admin_be.domain.users.controller;

import DGU_AI_LAB.admin_be.global.util.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth/email")
@RequiredArgsConstructor
public class EmailController {

    private final EmailService emailService;

    // 1) 이메일 인증번호 전송
    @PostMapping("/send")
    public ResponseEntity<Void> sendCode(@RequestParam String email) {
        emailService.sendEmailVerificationCode(email);
        return ResponseEntity.ok().build();
    }

    // 2) 인증번호 확인 및 인증 상태 저장 -> 3) AuthController의 /register로 이동
    @PostMapping("/verify")
    public ResponseEntity<Void> verifyCode(@RequestParam String email, @RequestParam String code) {
        emailService.confirmAuthCode(email, code);
        return ResponseEntity.ok().build();
    }
}
