package DGU_AI_LAB.admin_be.domain.users.controller.docs;

import DGU_AI_LAB.admin_be.domain.users.dto.request.PasswordUpdateRequestDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.request.PhoneUpdateRequestDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.request.UbuntuUsernameRegisterRequestDTO;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "2. 사용자 계정", description = "사용자 본인 정보 조회 및 수정 API")
public interface UserApi {

    @Operation(summary = "사용자 정보 확인", description = "로그인된 사용자의 상세 정보를 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패")
    })
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

    @Operation(summary = "우분투 유저네임 등록", description = "가입 시 우분투 유저네임을 받기 전에 만들어진 계정을 위한 1회성 등록 API입니다. 이미 등록되어 있으면 실패합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (형식 오류 등)"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "409", description = "이미 등록되어 있거나 다른 사용자가 사용 중인 유저네임")
    })
    @PatchMapping("/me/ubuntu-username")
    ResponseEntity<SuccessResponse<?>> registerUbuntuUsername(
            @AuthenticationPrincipal @Parameter(hidden = true) CustomUserDetails principal,
            @RequestBody @Valid UbuntuUsernameRegisterRequestDTO request
    );
}