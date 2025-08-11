package DGU_AI_LAB.admin_be.domain.users.controller;

import DGU_AI_LAB.admin_be.domain.users.controller.docs.AuthApi;
import DGU_AI_LAB.admin_be.domain.users.dto.request.UserLoginRequestDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.request.UserRegisterRequestDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.response.UserTokenResponseDTO;
import DGU_AI_LAB.admin_be.domain.users.service.UserLoginService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController implements AuthApi {

    private final UserLoginService userLoginService;

    /**
     * 3) 회원가입
     */
    @PostMapping("/register")
    public ResponseEntity<Void> register(@RequestBody @Valid UserRegisterRequestDTO request) {
        userLoginService.register(request);
        return ResponseEntity.ok().build();
    }


    /**
     * 로그인 (이메일 기반)
     */
    @PostMapping("/login")
    public ResponseEntity<UserTokenResponseDTO> login(@RequestBody @Valid UserLoginRequestDTO request) {
        return ResponseEntity.ok(userLoginService.login(request));
    }

}
