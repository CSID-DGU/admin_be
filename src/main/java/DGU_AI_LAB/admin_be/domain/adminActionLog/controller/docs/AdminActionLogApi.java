package DGU_AI_LAB.admin_be.domain.adminActionLog.controller.docs;

import DGU_AI_LAB.admin_be.domain.adminActionLog.dto.AdminActionLogSearchRequestDTO;
import DGU_AI_LAB.admin_be.global.common.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;

@Tag(name = "관리자 작업 기록", description = "관리자 작업 기록 조회 API")
public interface AdminActionLogApi {

    @Operation(
            summary = "관리자 작업 기록 조회",
            description = "최신부터 커서 방식으로 돌려줍니다. 대상으로 찾으려면 targetType·targetId 를, 관리자로 찾으려면 "
                    + "adminId·from·to 를 줍니다. failedOnly=true 면 실패(4xx·5xx)만 돌려줍니다.다음 페이지는 응답의 nextCursor 를 cursor 로 넘깁니다. "
                    + "result 는 상태 코드로 계산한 값(2xx SUCCESS, 4xx REJECTED, 5xx ERROR)이며, 202 로 등록된 비동기 "
                    + "작업의 최종 결과는 각 작업의 진행 상태 API 에서 확인합니다."
    )
    @ApiResponse(responseCode = "200", description = "성공")
    @ApiResponse(responseCode = "400", description = "조건이 잘못됨(targetType 없이 targetId, size 범위 밖 등)")
    ResponseEntity<SuccessResponse<?>> search(@ParameterObject AdminActionLogSearchRequestDTO condition);
}
