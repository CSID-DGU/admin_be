package DGU_AI_LAB.admin_be.domain.groups.controller.docs;

import DGU_AI_LAB.admin_be.domain.groups.dto.request.CreateGroupRequestDTO;
import DGU_AI_LAB.admin_be.global.common.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "2. 그룹 API", description = "사용자용 그룹 목록 조회, 그룹 생성 API")
public interface GroupApi {

    @Operation(summary = "모든 그룹 목록 조회", description = "시스템에 등록된 모든 그룹의 정보를 조회합니다. 사용 신청 단계에서 이 목록을 조회하고, 그룹을 선택할 수 있습니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "성공"),
            @ApiResponse(responseCode = "404", description = "조회된 그룹 정보 없음")
    })
    @GetMapping
    ResponseEntity<SuccessResponse<?>> getGroups();

    @Operation(summary = "새로운 그룹 생성", description = "새로운 그룹을 생성하고 DB에 저장합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "생성 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (필수 필드 누락 등)"),
            @ApiResponse(responseCode = "409", description = "중복된 GID를 가진 그룹이 이미 존재함")
    })
    @PostMapping
    ResponseEntity<SuccessResponse<?>> createGroup(@RequestBody @Valid CreateGroupRequestDTO dto);
}