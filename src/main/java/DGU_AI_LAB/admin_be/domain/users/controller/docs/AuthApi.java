package DGU_AI_LAB.admin_be.domain.users.controller.docs;

import DGU_AI_LAB.admin_be.domain.users.dto.request.UserAuthRequestDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.request.UserLoginRequestDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.request.UserRegisterRequestDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.response.UserAuthResponseDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.response.UserTokenResponseDTO;
import DGU_AI_LAB.admin_be.error.dto.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "1. 인증", description = "회원가입, 로그인, SSH 인증 API")
public interface AuthApi {

    @Operation(summary = "회원가입", description = "이메일 인증이 완료된 사용자가 계정을 생성합니다. 인증 미완료 시 401을 반환합니다.")
    @ApiResponse(responseCode = "200", description = "회원가입 성공")
    @ApiResponse(responseCode = "400", description = "필수 필드 누락 또는 형식 오류",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "이메일 인증이 완료되지 않은 경우",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "이미 가입된 이메일",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    ResponseEntity<Void> register(UserRegisterRequestDTO request);

    @Operation(summary = "로그인", description = "이메일과 비밀번호로 로그인하고 Access/Refresh 토큰을 반환합니다.")
    @ApiResponse(responseCode = "200", description = "로그인 성공 — Access/Refresh 토큰 반환",
            content = @Content(schema = @Schema(implementation = UserTokenResponseDTO.class)))
    @ApiResponse(responseCode = "401", description = "이메일 미존재, 비밀번호 불일치 또는 비활성화된 계정",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    ResponseEntity<UserTokenResponseDTO> login(UserLoginRequestDTO request);

    @Operation(
            summary = "SSH 비밀번호 인증",
            description = "인프라 서버에서 SSH 로그인 시 호출합니다. " +
                    "username과 Base64 인코딩된 비밀번호를 DB 저장값과 비교하여 인증 결과를 반환합니다."
    )
    @ApiResponse(responseCode = "200", description = "인증 결과 반환 (success 필드로 확인)",
            content = @Content(schema = @Schema(implementation = UserAuthResponseDTO.class)))
    @ApiResponse(responseCode = "400", description = "필수 필드 누락",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    ResponseEntity<UserAuthResponseDTO> userAuth(UserAuthRequestDTO dto);
}
