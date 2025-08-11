package DGU_AI_LAB.admin_be.domain.users.controller.docs;

import DGU_AI_LAB.admin_be.global.common.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "회원가입 이메일 인증", description = "이메일 인증번호 발송 및 인증 확인 API")
public interface EmailApi {

    @Operation(summary = "이메일 인증번호 발송", description = "입력한 이메일 주소로 인증번호를 전송합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "인증번호 발송 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청")
    })
    ResponseEntity<SuccessResponse<?>> sendCode(
            @Parameter(description = "인증번호를 받을 이메일 주소", example = "test@example.com")
            @RequestParam String email
    );

    @Operation(summary = "이메일 인증번호 확인", description = "입력한 인증번호가 유효한지 검증하고 인증 상태를 저장합니다. (제한시간: 5분)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "인증 성공"),
            @ApiResponse(responseCode = "400", description = "인증번호 불일치 또는 만료됨")
    })
    ResponseEntity<SuccessResponse<?>> verifyCode(
            @Parameter(description = "이메일 주소", example = "test@example.com")
            @RequestParam String email,

            @Parameter(description = "사용자가 입력한 인증번호", example = "123456")
            @RequestParam String code
    );
}
