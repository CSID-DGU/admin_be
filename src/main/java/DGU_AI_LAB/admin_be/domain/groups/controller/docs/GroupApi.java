package DGU_AI_LAB.admin_be.domain.groups.controller.docs;

import DGU_AI_LAB.admin_be.domain.groups.dto.request.CreateGroupRequestDTO;
import DGU_AI_LAB.admin_be.global.auth.CustomUserDetails;
import DGU_AI_LAB.admin_be.global.common.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "2. 사용자 그룹", description = "우분투 그룹 조회 및 생성 API")
public interface GroupApi {

    @Operation(summary = "그룹 목록 조회", description = "시스템에 등록된 모든 우분투 그룹을 조회합니다. 서버 사용 신청 시 소속 그룹 선택에 활용합니다.")
    @ApiResponse(responseCode = "200", description = "성공")
    @GetMapping
    ResponseEntity<SuccessResponse<?>> getGroups();

    @Operation(
            summary = "그룹 생성 작업 등록",
            description = "그룹을 만드는 작업을 등록하고 작업 번호를 돌려줍니다. 그룹(GID 포함)은 작업이 끝나야 생기므로, " +
                    "응답의 operationId로 GET /api/groups/operations/{operationId}를 조회해 완료를 확인합니다. " +
                    "groupName은 필수이며, ubuntuUsername은 생략 가능합니다(생략 시 멤버 없는 그룹 생성)."
    )
    @ApiResponse(responseCode = "202", description = "작업 등록됨")
    @ApiResponse(responseCode = "400", description = "groupName 누락 또는 형식 오류, 계정 이름과 같은 그룹명")
    @ApiResponse(responseCode = "403", description = "ubuntuUsername이 로그인 사용자와 불일치")
    @ApiResponse(responseCode = "409", description = "동일한 그룹명 중복, 같은 이름을 만드는 작업이 진행 중")
    @ApiResponse(responseCode = "429", description = "하루 생성 한도 초과")
    @ApiResponse(responseCode = "502", description = "작업 등록 실패")
    @PostMapping
    ResponseEntity<SuccessResponse<?>> createGroup(
            @RequestBody @Valid CreateGroupRequestDTO dto,
            @AuthenticationPrincipal @Parameter(hidden = true) CustomUserDetails principal
    );

    @Operation(
            summary = "그룹 작업 진행 상태 조회",
            description = "그룹 생성·멤버 추가·제거 작업의 상태(PROCESSING/APPLIED/FAILED)를 돌려줍니다. " +
                    "생성이 끝났으면 만들어진 그룹이 함께 옵니다. 요청한 사람·대상 계정의 주인·관리자만 볼 수 있습니다."
    )
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "404", description = "작업이 없거나 볼 수 없는 작업")
    @GetMapping("/operations/{operationId}")
    ResponseEntity<SuccessResponse<?>> getGroupOperation(
            @PathVariable @Parameter(description = "그룹 작업 번호") Long operationId,
            @AuthenticationPrincipal @Parameter(hidden = true) CustomUserDetails principal
    );
}
