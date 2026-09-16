package DGU_AI_LAB.admin_be.domain.requests.controller.docs;

import DGU_AI_LAB.admin_be.domain.requests.dto.request.ApprovalRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.RejectionRequestDTO;
import io.swagger.v3.oas.annotations.media.Content;

import DGU_AI_LAB.admin_be.domain.requests.dto.request.ApproveRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.MigratePodRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.RejectRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.ContainerInfoDTO;
import DGU_AI_LAB.admin_be.error.dto.ErrorResponse;
import DGU_AI_LAB.admin_be.global.common.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

import java.util.List;

@Tag(name = "3. 관리자 신청 관리", description = "서버 사용 신청 승인·거절 및 컨테이너 현황 조회 API")
public interface AdminRequestApi {

    @Operation(summary = "전체 신청 목록 조회", description = "모든 상태의 서버 사용 신청 목록을 조회합니다.")
    @ApiResponse(responseCode = "200", description = "성공")
    ResponseEntity<SuccessResponse<?>> getAllRequests();

    @Operation(summary = "전체 리소스 사용량 조회", description = "FULFILLED 상태인 모든 서버의 리소스 사용량을 조회합니다.")
    @ApiResponse(responseCode = "200", description = "성공")
    ResponseEntity<SuccessResponse<?>> getAllResourceUsage();

    @Operation(summary = "활성 컨테이너 목록 조회", description = "현재 활성화된 모든 컨테이너 정보(ubuntuUsername, Pod명, 노드명 등)를 조회합니다.")
    @ApiResponse(responseCode = "200", description = "성공",
            content = @Content(schema = @Schema(implementation = ContainerListResponseDoc.class)))
    ResponseEntity<SuccessResponse<?>> getAllActiveContainers();

    @Schema(name = "SuccessResponseListContainerInfoDTO", description = "활성 컨테이너 목록 응답")
    record ContainerListResponseDoc(int status, String message, List<ContainerInfoDTO> data) {}

    @Operation(summary = "사용 신청 승인", description = "PENDING 상태의 신청을 PROCESSING으로 바꾸고 계정·컨테이너 생성 작업을 등록합니다.")
    @ApiResponse(responseCode = "202", description = "생성 작업 등록됨 — 결과는 신청 상태로 확인")
    @ApiResponse(responseCode = "404", description = "신청 또는 리소스를 찾을 수 없음",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "우분투 계정명 중복",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    ResponseEntity<SuccessResponse<?>> approveRequest(Long requestId, ApprovalRequestDTO dto);

    @Operation(summary = "사용 신청 거절", description = "PENDING 또는 FULFILLED 상태의 신청을 거절 처리합니다.")
    @ApiResponse(responseCode = "200", description = "성공")
    @ApiResponse(responseCode = "404", description = "신청을 찾을 수 없음",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "400", description = "이미 거절/삭제된 상태",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    ResponseEntity<SuccessResponse<?>> rejectRequest(Long requestId, RejectionRequestDTO dto);

    @Operation(summary = "신청 작업 단계 기록 조회",
            description = "신청의 생성(승인)·회수 작업을 최근 순으로 최대 5개씩, 작업마다 단계별 결과(성공·실패·재시도), "
                    + "시각(UTC), 시도 번호, 접근 시험 요약과 함께 조회합니다.")
    @ApiResponse(responseCode = "200", description = "성공 — 작업이 없으면 jobs가 빈 목록")
    @ApiResponse(responseCode = "502", description = "config-server 작업 기록 조회 실패",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    ResponseEntity<SuccessResponse<?>> getJobSteps(Long requestId);

    @Operation(summary = "마이그레이션 작업 등록", description = "FULFILLED 상태 신청을 MIGRATING으로 바꾸고 다른 노드로 옮기는 작업을 등록합니다. " +
            "force면 개선 비율을 보지 않습니다. 결과는 신청 상태와 마지막 마이그레이션 결과로 확인합니다. 홈 디렉터리만 유지됩니다.")
    @ApiResponse(responseCode = "202", description = "작업 등록됨")
    @ApiResponse(responseCode = "404", description = "신청을 찾을 수 없음", content = @Content)
    @ApiResponse(responseCode = "409", description = "FULFILLED 상태가 아니거나 이미 진행 중", content = @Content)
    @ApiResponse(responseCode = "422", description = "config-server가 요청을 거절함", content = @Content)
    @ApiResponse(responseCode = "502", description = "config-server 작업 등록 실패", content = @Content)
    ResponseEntity<SuccessResponse<?>> startMigration(Long requestId, MigratePodRequestDTO dto);

    @Operation(summary = "마지막 마이그레이션 결과", description = "신청의 마지막 마이그레이션 작업 phase와 결과(migrated/skipped, 노드, 기존 Pod 정리 여부)를 조회합니다.")
    @ApiResponse(responseCode = "200", description = "성공 — 작업이 없으면 phase가 none")
    ResponseEntity<SuccessResponse<?>> getLatestMigration(Long requestId);

    @Operation(summary = "컨테이너 회수", description = "신청 하나의 컨테이너만 회수합니다. 우분투 계정과 홈 디렉터리는 남고, "
            + "같은 사용자의 다른 컨테이너는 영향받지 않습니다. 계정까지 회수하려면 사용자 관리의 계정 회수를 사용하세요.")
    @ApiResponse(responseCode = "200", description = "회수 완료")
    @ApiResponse(responseCode = "404", description = "신청을 찾을 수 없음", content = @Content)
    @ApiResponse(responseCode = "409", description = "FULFILLED 상태가 아님", content = @Content)
    @ApiResponse(responseCode = "502", description = "config-server 회수 실패", content = @Content)
    ResponseEntity<SuccessResponse<?>> deleteContainer(Long requestId);
}