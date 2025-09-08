package DGU_AI_LAB.admin_be.domain.groups.controller.docs;

import DGU_AI_LAB.admin_be.domain.groups.dto.request.CreateGroupRequestDTO;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "2. 사용자 그룹 API", description = "사용자용 우분투 그룹 관련 API")
public interface GroupApi {

    @Operation(summary = "모든 그룹 목록 조회", description = "시스템에 등록된 모든 그룹의 정보를 조회합니다. 사용 신청 단계에서 이 목록을 조회하고, 그룹을 선택할 수 있습니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "성공"),
            @ApiResponse(responseCode = "404", description = "조회된 그룹 정보 없음")
    })
    @GetMapping
    ResponseEntity<SuccessResponse<?>> getGroups();

    @Operation(summary = "새로운 그룹 생성", description = "새로운 그룹을 생성하고 MySQL DB에 저장합니다. 이후 Config Server에 그룹 생성 API를 호출합니다. 그룹명과 사용자 이름은 필수값이지만, 사용자 이름은 생략 가능하며 이 경우 멤버 없는 그룹이 생성됩니다. " +
            "사용 신청을 아직 생성하지 않았지만, 그룹을 먼저 생성해야 하는 경우를 고려했습니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "생성 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청: 필수 필드 누락(groupName) 또는 잘못된 형식"),
            @ApiResponse(responseCode = "403", description = "접근 금지: 요청한 ubuntuUsername이 로그인한 사용자와 일치하지 않음"),
            @ApiResponse(responseCode = "409", description = "데이터 충돌: 동일한 그룹명이 이미 존재함"),
            @ApiResponse(responseCode = "500", description = "서버 오류: GID 할당 실패 또는 외부 API 호출 실패")
    })
    @PostMapping
    ResponseEntity<SuccessResponse<?>> createGroup(
            @RequestBody @Valid CreateGroupRequestDTO dto,
            @AuthenticationPrincipal @Parameter(hidden = true) CustomUserDetails principal
    );
}