package DGU_AI_LAB.admin_be.domain.users.controller.docs;

import DGU_AI_LAB.admin_be.domain.users.dto.request.UserAuthRequestDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.request.UserLoginRequestDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.request.UserRegisterRequestDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.response.UserAuthResponseDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.response.UserTokenResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "0. 인증", description = "회원가입 및 로그인 API")
public interface AuthApi {

    @Operation(
            summary = "회원가입",
            description = "이메일 인증이 완료된 사용자가 회원가입을 진행합니다.\n" +
                    "이메일 인증이 완료되지 않으면, 예외를 반환합니다.\n" +
                    "이메일을 기준으로 인증 여부를 조회합니다.",
            requestBody = @RequestBody(
                    required = true,
                    description = "회원가입 요청 정보",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = UserRegisterRequestDTO.class)
                    )
            )
    )
    @ApiResponse(responseCode = "200", description = "회원가입 성공")
    ResponseEntity<Void> register(UserRegisterRequestDTO request);

    @Operation(
            summary = "로그인",
            description = "이메일과 비밀번호로 로그인하고 Access/Refresh 토큰을 발급받습니다.",
            requestBody = @RequestBody(
                    required = true,
                    description = "로그인 요청 정보",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = UserLoginRequestDTO.class)
                    )
            )
    )
    @ApiResponse(responseCode = "200", description = "로그인 성공 및 토큰 발급")
    ResponseEntity<UserTokenResponseDTO> login(UserLoginRequestDTO request);

    @Operation(
            summary = "SSH 로그인",
            description = "사용자가 입력한 username과 password를 request에 있는 정보와 비교합니다.",
            requestBody = @RequestBody(
                    required = true,
                    description = "SSH 로그인 인증 정보",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = UserAuthRequestDTO.class)
                    )
            )
    )
    @ApiResponse(
            responseCode = "200",
            description = "인증 결과 반환",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = UserAuthResponseDTO.class)
            )
    )
    ResponseEntity<UserAuthResponseDTO> userAuth(UserAuthRequestDTO request);
}
