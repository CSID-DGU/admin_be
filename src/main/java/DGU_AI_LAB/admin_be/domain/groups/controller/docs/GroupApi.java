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
            summary = "공유 그룹 만들기",
            description = "그룹을 DB에만 만들고 바로 돌려줍니다(ubuntuGid는 null — 승인 대기 그룹). 다른 사용자도 목록에서 " +
                    "바로 고를 수 있습니다. 인프라 그룹과 GID는 이 그룹을 고른 신청이 관리자 승인을 받아 컨테이너가 만들어질 때 " +
                    "생기고, 그 그룹을 고른 신청이 모두 거절·취소되면 그룹도 지워집니다."
    )
    @ApiResponse(responseCode = "201", description = "생성됨")
    @ApiResponse(responseCode = "400", description = "groupName 누락 또는 형식 오류, 계정 이름과 같은 그룹명")
    @ApiResponse(responseCode = "409", description = "동일한 그룹명 중복, 시스템 예약 이름, 같은 이름을 만드는 작업이 진행 중")
    @ApiResponse(responseCode = "429", description = "하루 생성 한도 초과")
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
