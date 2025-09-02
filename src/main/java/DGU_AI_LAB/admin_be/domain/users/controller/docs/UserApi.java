package DGU_AI_LAB.admin_be.domain.users.controller.docs;

import DGU_AI_LAB.admin_be.domain.users.dto.request.PasswordUpdateRequestDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.request.PhoneUpdateRequestDTO;
import DGU_AI_LAB.admin_be.global.auth.CustomUserDetails;
import DGU_AI_LAB.admin_be.global.common.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "2. 사용자용 마이 정보 관리", description = "일반 사용자 본인 정보 관리 API")
public interface UserApi {

    @Operation(summary = "사용자 정보 확인", description = "로그인된 사용자의 상세 정보를 조회합니다.")
    @GetMapping("/me")
    ResponseEntity<SuccessResponse<?>> getMyInfo(
            @AuthenticationPrincipal CustomUserDetails principal
    );

    @Operation(summary = "사용자 비밀번호 변경", description = "로그인된 사용자의 비밀번호를 변경합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (현재 비밀번호 불일치 등)"),
            @ApiResponse(responseCode = "401", description = "인증 실패")
    })
    @PatchMapping("/me/password")
    ResponseEntity<SuccessResponse<?>> updateUserPassword(
            @AuthenticationPrincipal @Parameter(hidden = true) CustomUserDetails principal,
            @RequestBody @Valid PasswordUpdateRequestDTO request
    );

    @Operation(summary = "사용자 연락처 변경", description = "로그인된 사용자의 연락처 정보를 변경합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (연락처 형식 오류 등)"),
            @ApiResponse(responseCode = "401", description = "인증 실패")
    })
    @PatchMapping("/me/phone")
    ResponseEntity<SuccessResponse<?>> updateUserPhone(
            @AuthenticationPrincipal @Parameter(hidden = true) CustomUserDetails principal,
            @RequestBody @Valid PhoneUpdateRequestDTO request
    );
}