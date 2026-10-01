package DGU_AI_LAB.admin_be.domain.users.controller.docs;

import DGU_AI_LAB.admin_be.domain.users.dto.request.EmailSendRequestDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.request.PasswordResetRequestDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.request.UserLoginRequestDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.request.UserRegisterRequestDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.response.UserTokenResponseDTO;
import DGU_AI_LAB.admin_be.error.dto.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "1. 인증", description = "회원가입, 로그인 API")
public interface AuthApi {

    @Operation(summary = "회원가입", description = "이메일 인증이 완료된 사용자가 계정을 생성합니다. 인증 미완료 시 401을 반환합니다.")
    @ApiResponse(responseCode = "201", description = "회원가입 성공")
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

    @Operation(summary = "비밀번호 재설정 인증번호 발송", description = "가입된 이메일이면 재설정 인증번호를 보냅니다(제한시간 5분). 가입 여부가 드러나지 않게 가입되지 않은 이메일에도 200을 반환합니다.")
    @ApiResponse(responseCode = "200", description = "요청 접수 — 가입된 이메일이면 인증번호 발송")
    @ApiResponse(responseCode = "429", description = "같은 이메일로 너무 자주 요청",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    ResponseEntity<Void> sendPasswordResetCode(EmailSendRequestDTO request);

    @Operation(summary = "비밀번호 재설정 신청", description = "메일로 받은 인증번호와 새 비밀번호로 재설정을 신청합니다. 신청만으로는 아무것도 바뀌지 않고, 관리자가 승인하면 웹 비밀번호와 SSH 비밀번호가 함께 바뀝니다. 결과는 메일로 알립니다. 승인 전에 다시 신청하면 새 비밀번호만 바뀝니다. 인증번호는 한 번만 쓸 수 있습니다.")
    @ApiResponse(responseCode = "202", description = "신청 접수 — 관리자 승인 대기")
    @ApiResponse(responseCode = "400", description = "인증번호 불일치·만료 또는 비밀번호 형식 오류",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "앞선 신청을 컨테이너에 반영하는 중",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "429", description = "인증번호 입력 횟수 초과",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    ResponseEntity<Void> requestPasswordReset(PasswordResetRequestDTO request);
}
