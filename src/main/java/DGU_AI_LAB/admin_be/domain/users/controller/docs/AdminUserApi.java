package DGU_AI_LAB.admin_be.domain.users.controller.docs;

import DGU_AI_LAB.admin_be.global.common.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "1. 관리자 유저 관리", description = "관리자용 사용자 계정 관리 API")
public interface AdminUserApi {

    @Operation(summary = "사용자 단일 조회", description = "ID를 통해 특정 사용자의 상세 정보를 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "성공"),
            @ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음")
    })
    @GetMapping("/{id}")
    ResponseEntity<SuccessResponse<?>> getUser(@PathVariable @Parameter(description = "사용자 ID") Long id);

    @Operation(summary = "전체 사용자 목록 조회", description = "등록된 모든 사용자의 요약 정보를 조회합니다.")
    @GetMapping
    ResponseEntity<SuccessResponse<?>> getAllUsers();

    @Operation(summary = "사용자 계정 비활성화 (Soft Delete)", description = "사용자 계정을 비활성화(탈퇴)합니다. DB에서 isActive 상태를 false로 변경하며, 사용자는 더 이상 로그인할 수 없습니다." +
            "사용자와 관련된 모든 우분투 계정이 삭제됩니다. ")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "성공"),
            @ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음")
    })
    @DeleteMapping("/{id}")
    ResponseEntity<SuccessResponse<?>> deleteUser(@PathVariable @Parameter(description = "사용자 ID") Long id);

    @Operation(summary = "우분투 계정 삭제", description = "특정 우분투 계정을 외부 Config Server에 요청하여 삭제하고, 해당 요청(Request)을 DB에서 논리적으로 삭제(DELETED 상태) 처리합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "성공"),
            @ApiResponse(responseCode = "404", description = "우분투 계정(요청)을 찾을 수 없음"),
            @ApiResponse(responseCode = "502", description = "외부 서버 오류 또는 API 연동 실패")
    })
    @DeleteMapping("/ubuntu/{username}")
    ResponseEntity<SuccessResponse<?>> deleteUbuntuAccount(
            @PathVariable @Parameter(description = "우분투 계정명") String username
    );
}