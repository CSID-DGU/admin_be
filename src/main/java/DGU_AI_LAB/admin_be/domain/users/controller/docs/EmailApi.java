package DGU_AI_LAB.admin_be.domain.users.controller.docs;

import DGU_AI_LAB.admin_be.domain.users.dto.request.EmailSendRequestDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.request.EmailVerifyRequestDTO;
import DGU_AI_LAB.admin_be.global.common.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "1. 인증", description = "회원가입, 로그인 API")
public interface EmailApi {

    @Operation(summary = "이메일 인증번호 발송", description = "요청 본문의 이메일 주소로 인증번호를 전송합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "인증번호 발송 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청")
    })
    ResponseEntity<SuccessResponse<?>> sendCode(EmailSendRequestDTO request);

    @Operation(summary = "이메일 인증번호 확인", description = "입력한 인증번호가 유효한지 검증하고 인증 상태를 저장합니다. (제한시간: 5분)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "인증 성공"),
            @ApiResponse(responseCode = "400", description = "인증번호 불일치 또는 만료됨")
    })
    ResponseEntity<SuccessResponse<?>> verifyCode(EmailVerifyRequestDTO request);
}
