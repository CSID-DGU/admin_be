package DGU_AI_LAB.admin_be.domain.users.controller.docs;

import DGU_AI_LAB.admin_be.global.common.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "관리자 비밀번호 재설정", description = "비밀번호 재설정 신청 조회·승인·거절 API")
public interface AdminPasswordResetApi {

    @Operation(summary = "처리할 재설정 신청 목록", description = "승인 대기(PENDING)와 컨테이너 반영 중(PROCESSING)인 신청을 최근 순으로 돌려준다.")
    @ApiResponse(responseCode = "200", description = "성공")
    ResponseEntity<SuccessResponse<?>> getOpenRequests();

    @Operation(
            summary = "재설정 신청 승인",
            description = "리눅스 계정이 있으면 컨테이너에 반영하는 작업을 등록하고 돌아온다(status=PROCESSING). 작업이 성공하면 "
                    + "웹 비밀번호와 SSH(Ubuntu) 비밀번호가 함께 바뀌고 신청자의 기존 로그인 세션이 끊기며, 신청자에게 메일로 알린다. "
                    + "작업이 실패하면 신청이 승인 대기로 돌아오고 관리자 Slack으로 알린다. 리눅스 계정이 없으면 바로 적용된다(status=APPLIED)."
    )
    @ApiResponse(responseCode = "202", description = "접수 — 반영 중(PROCESSING)이거나 바로 적용됨(APPLIED)")
    @ApiResponse(responseCode = "404", description = "신청을 찾을 수 없음")
    @ApiResponse(responseCode = "409", description = "이미 처리된 신청이거나, 컨테이너 생성 중이라 지금은 바꿀 수 없음")
    @ApiResponse(responseCode = "502", description = "작업 등록 실패 (신청은 승인 대기로 남음, 다시 승인)")
    ResponseEntity<SuccessResponse<?>> approve(
            @Parameter(hidden = true) Long adminId,
            @Parameter(description = "재설정 신청 번호") Long passwordResetRequestId
    );

    @Operation(summary = "재설정 신청 거절", description = "승인 대기인 신청을 거절한다. 비밀번호는 바뀌지 않고, 신청자에게 메일로 알린다.")
    @ApiResponse(responseCode = "200", description = "성공")
    @ApiResponse(responseCode = "404", description = "신청을 찾을 수 없음")
    @ApiResponse(responseCode = "409", description = "이미 처리됐거나 컨테이너에 반영하는 중인 신청")
    ResponseEntity<SuccessResponse<?>> deny(
            @Parameter(hidden = true) Long adminId,
            @Parameter(description = "재설정 신청 번호") Long passwordResetRequestId
    );
}
