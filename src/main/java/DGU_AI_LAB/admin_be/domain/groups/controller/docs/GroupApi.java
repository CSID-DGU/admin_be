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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "2. 사용자 그룹", description = "우분투 그룹 조회 및 생성 API")
public interface GroupApi {

    @Operation(summary = "그룹 목록 조회", description = "시스템에 등록된 모든 우분투 그룹을 조회합니다. 서버 사용 신청 시 소속 그룹 선택에 활용합니다.")
    @ApiResponse(responseCode = "200", description = "성공")
    @GetMapping
    ResponseEntity<SuccessResponse<?>> getGroups();

    @Operation(
            summary = "그룹 생성",
            description = "인프라 서버에 그룹 생성을 요청하고, 확정된 GID를 DB에 저장합니다. " +
                    "groupName은 필수이며, ubuntuUsername은 생략 가능합니다(생략 시 멤버 없는 그룹 생성)."
    )
    @ApiResponse(responseCode = "201", description = "생성 성공")
    @ApiResponse(responseCode = "400", description = "groupName 누락 또는 형식 오류")
    @ApiResponse(responseCode = "403", description = "ubuntuUsername이 로그인 사용자와 불일치")
    @ApiResponse(responseCode = "409", description = "동일한 그룹명 중복")
    @ApiResponse(responseCode = "502", description = "인프라 서버 응답 오류")
    @PostMapping
    ResponseEntity<SuccessResponse<?>> createGroup(
            @RequestBody @Valid CreateGroupRequestDTO dto,
            @AuthenticationPrincipal @Parameter(hidden = true) CustomUserDetails principal
    );
}
