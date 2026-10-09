package DGU_AI_LAB.admin_be.domain.warnings.controller.docs;

import DGU_AI_LAB.admin_be.domain.warnings.dto.WarningReasonRequestDTO;
import DGU_AI_LAB.admin_be.global.common.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;

@Tag(name = "3. 관리자 유저 관리", description = "사용자 계정 조회 및 삭제 API")
public interface AdminWarningApi {

    @Operation(summary = "사용자의 경고 현황 조회", description = "현재 횟수, 차감 가능 여부, 이용 정지 종료 시각, 내역을 돌려줍니다.")
    @ApiResponse(responseCode = "200", description = "성공")
    @ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음")
    ResponseEntity<SuccessResponse<?>> getWarnings(@Parameter(description = "사용자 ID") Long userId);

    @Operation(
            summary = "경고 부여",
            description = "경고를 하나 줍니다. 2회부터는 그 시각에 이용 정지 7 × (횟수 − 1)일이 시작되고, 그 계정의 모든 컨테이너의 "
                    + "모든 접속 포트가 막힙니다. 컨테이너와 실행 중인 작업은 그대로 둡니다. 관리자에게는 줄 수 없습니다."
    )
    @ApiResponse(responseCode = "201", description = "부여됨")
    @ApiResponse(responseCode = "400", description = "관리자에게는 경고를 줄 수 없음")
    ResponseEntity<SuccessResponse<?>> grantWarning(@Parameter(description = "사용자 ID") Long userId, Long adminId,
                                                    WarningReasonRequestDTO dto);

    @Operation(
            summary = "경고 차감",
            description = "절차를 지킨 신고에 따라 경고를 하나 뺍니다. 경고가 3회 이상 쌓인 적이 있어야 하며 0회까지 내려갑니다. "
                    + "진행 중인 이용 정지에는 영향이 없습니다."
    )
    @ApiResponse(responseCode = "201", description = "차감됨")
    @ApiResponse(responseCode = "409", description = "차감할 수 없음(3회 이상 쌓인 적이 없거나 횟수가 0)")
    ResponseEntity<SuccessResponse<?>> deductWarning(@Parameter(description = "사용자 ID") Long userId, Long adminId,
                                                     WarningReasonRequestDTO dto);

    @Operation(
            summary = "경고 취소",
            description = "잘못 준 경고를 취소합니다. 그 경고는 처음부터 없던 것으로 세고, 그 경고로 시작된 이용 정지가 아직 "
                    + "끝나지 않았으면 바로 끝냅니다."
    )
    @ApiResponse(responseCode = "200", description = "취소됨")
    @ApiResponse(responseCode = "404", description = "그 사용자의 경고가 아님")
    @ApiResponse(responseCode = "409", description = "이미 취소된 경고")
    ResponseEntity<SuccessResponse<?>> cancelWarning(@Parameter(description = "사용자 ID") Long userId,
                                                     @Parameter(description = "취소할 경고(부여) 번호") Long warningId,
                                                     Long adminId,
                                                     @ParameterObject WarningReasonRequestDTO dto);
}
