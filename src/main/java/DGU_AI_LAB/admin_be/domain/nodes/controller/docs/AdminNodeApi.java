package DGU_AI_LAB.admin_be.domain.nodes.controller.docs;

import DGU_AI_LAB.admin_be.global.common.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "3. 관리자 시스템", description = "노드 조회 API")
public interface AdminNodeApi {

    @Operation(summary = "전체 노드 목록 조회", description = "마이그레이션 후보 노드 선택 등에 사용할 전체 노드 목록을 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    ResponseEntity<SuccessResponse<?>> getAllNodes();
}
