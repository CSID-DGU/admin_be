package DGU_AI_LAB.admin_be.domain.users.controller.docs;

import DGU_AI_LAB.admin_be.domain.users.dto.request.UserLoginRequestDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.response.UserTokenResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;

import jakarta.validation.Valid;

@Tag(name = "인증", description = "로그인 및 회원가입 API")
public interface AuthApi {

    @Operation(summary = "유저/관리자 로그인", description = "이메일/비밀번호로 로그인하여 액세스 토큰을 발급받습니다.")
    @ApiResponse(
            responseCode = "200",
            description = "로그인 성공",
            content = @Content(schema = @Schema(implementation = UserTokenResponseDTO.class))
    )
    ResponseEntity<UserTokenResponseDTO> login(@RequestBody @Valid UserLoginRequestDTO request);

}
