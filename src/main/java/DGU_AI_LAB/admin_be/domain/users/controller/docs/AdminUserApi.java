package DGU_AI_LAB.admin_be.domain.users.controller.docs;

import DGU_AI_LAB.admin_be.domain.users.dto.request.UserActivationRequestDTO;

import DGU_AI_LAB.admin_be.domain.users.dto.request.ChangeRoleRequestDTO;
import DGU_AI_LAB.admin_be.global.common.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "3. 관리자 유저 관리", description = "사용자 계정 조회 및 삭제 API")
public interface AdminUserApi {

    @Operation(summary = "사용자 단일 조회", description = "ID로 특정 사용자의 상세 정보를 조회합니다.")
    @ApiResponse(responseCode = "200", description = "성공")
    @ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음")
    @GetMapping("/{id}")
    ResponseEntity<SuccessResponse<?>> getUser(@PathVariable @Parameter(description = "사용자 ID") Long id);

    @Operation(summary = "전체 사용자 목록 조회", description = "등록된 모든 사용자의 요약 정보를 조회합니다.")
    @ApiResponse(responseCode = "200", description = "성공")
    @GetMapping
    ResponseEntity<SuccessResponse<?>> getAllUsers();

    @Operation(
            summary = "사용자 계정 비활성화",
            description = "사용자 계정을 소프트 딜리트합니다. isActive를 false로 변경하며 해당 사용자의 모든 우분투 계정이 함께 삭제됩니다."
    )
    @ApiResponse(responseCode = "200", description = "성공")
    @ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음")
    @DeleteMapping("/{id}")
    ResponseEntity<SuccessResponse<?>> deleteUser(@PathVariable @Parameter(description = "사용자 ID") Long id);

    @Operation(summary = "사용자의 우분투 계정 회수", description = "사용자의 살아 있는 신청 컨테이너를 모두 회수하고 우분투 계정을 지웁니다. 홈 디렉터리는 보존합니다.")
    @ApiResponse(responseCode = "200", description = "성공")
    @ApiResponse(responseCode = "404", description = "사용자 또는 우분투 계정이 없음")
    @ApiResponse(responseCode = "502", description = "config-server 회수 작업 실패")
    ResponseEntity<SuccessResponse<?>> deleteUbuntuAccount(@PathVariable @Parameter(description = "사용자 ID") Long id);

    @Operation(summary = "사용자 활성 상태 변경", description = "active=false면 비활성화(컨테이너·계정 정리 포함), true면 재활성화합니다.")
    @ApiResponse(responseCode = "200", description = "성공")
    @ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음")
    @ApiResponse(responseCode = "409", description = "이미 그 상태이거나 마이그레이션이 진행 중인 요청이 있음")
    ResponseEntity<SuccessResponse<?>> updateUserActivation(
            @PathVariable @Parameter(description = "사용자 ID") Long id,
            @RequestBody @Valid UserActivationRequestDTO dto
    );

    @Operation(
            summary = "사용자 권한 변경",
            description = "사용자의 권한(ADMIN/USER)을 변경한다."
    )
    @ApiResponse(responseCode = "200", description = "성공")
    @ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음")
    @ApiResponse(responseCode = "409", description = "이미 해당 권한을 가진 사용자")
    @PatchMapping("/{id}/role")
    ResponseEntity<SuccessResponse<?>> changeUserRole(
            @PathVariable @Parameter(description = "사용자 ID") Long id,
            @RequestBody @Valid ChangeRoleRequestDTO dto
    );
}