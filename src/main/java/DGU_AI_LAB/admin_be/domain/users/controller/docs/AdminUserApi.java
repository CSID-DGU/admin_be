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
            description = "사용자 계정을 소프트 딜리트합니다. isActive를 바로 false로 바꾸고, 소유한 컨테이너와 우분투 계정의 회수 작업을 등록합니다. "
                    + "회수는 뒤에서 진행되며 신청은 EXPIRING → DELETED, 우분투 계정은 RELEASING → NONE이 됩니다."
    )
    @ApiResponse(responseCode = "202", description = "회수 작업 등록됨")
    @ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음")
    @DeleteMapping("/{id}")
    ResponseEntity<SuccessResponse<?>> deleteUser(@PathVariable @Parameter(description = "사용자 ID") Long id);

    @Operation(summary = "사용자의 우분투 계정 회수", description = "사용자의 살아 있는 신청 컨테이너를 모두 회수한 뒤 우분투 계정을 지웁니다. 홈 디렉터리는 보존합니다. "
            + "작업만 등록하고 돌아오며, 우분투 계정 상태가 RELEASING → NONE이 되면 끝난 것입니다. 회수 중에 다시 부르면 실패한 단계를 다시 등록합니다.")
    @ApiResponse(responseCode = "202", description = "회수 작업 등록됨")
    @ApiResponse(responseCode = "404", description = "사용자 또는 우분투 계정이 없음")
    @ApiResponse(responseCode = "409", description = "승인·마이그레이션 진행 중인 신청이 있음")
    @ApiResponse(responseCode = "502", description = "일부 컨테이너의 회수 작업 등록 실패")
    ResponseEntity<SuccessResponse<?>> deleteUbuntuAccount(@PathVariable @Parameter(description = "사용자 ID") Long id);

    @Operation(summary = "사용자 활성 상태 변경", description = "active=false면 비활성화(컨테이너·계정 정리 포함), true면 재활성화합니다.")
    @ApiResponse(responseCode = "200", description = "성공")
    @ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음")
    @ApiResponse(responseCode = "409", description = "이미 그 상태이거나 마이그레이션이 진행 중인 요청이 있음")
    ResponseEntity<SuccessResponse<?>> updateUserActivation(
            @PathVariable @Parameter(description = "사용자 ID") Long id,
            @RequestBody @Valid UserActivationRequestDTO dto
    );

    @Operation(summary = "사용자의 공용 그룹 조회", description = "계정에 실제로 반영된(AD 기준) 공용 그룹 목록을 이름순으로 반환합니다.")
    @ApiResponse(responseCode = "200", description = "성공")
    @ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음")
    @GetMapping("/{id}/groups")
    ResponseEntity<SuccessResponse<?>> getUserGroups(@PathVariable @Parameter(description = "사용자 ID") Long id);

    @Operation(summary = "사용자를 공용 그룹에서 제거",
            description = "AD에서 멤버십을 빼고, 떠 있는 컨테이너에 반영한 뒤 DB를 맞춥니다. 이미 빠져 있어도 성공입니다. "
                    + "팀 디렉터리와 그 안의 파일은 그대로 둡니다.")
    @ApiResponse(responseCode = "200", description = "성공")
    @ApiResponse(responseCode = "404", description = "사용자 또는 그룹이 없음")
    @ApiResponse(responseCode = "409", description = "계정의 기본 그룹")
    @ApiResponse(responseCode = "502", description = "AD 반영 실패")
    @DeleteMapping("/{id}/groups/{groupId}")
    ResponseEntity<SuccessResponse<?>> removeUserFromGroup(
            @PathVariable @Parameter(description = "사용자 ID") Long id,
            @PathVariable @Parameter(description = "그룹 ID") Long groupId
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