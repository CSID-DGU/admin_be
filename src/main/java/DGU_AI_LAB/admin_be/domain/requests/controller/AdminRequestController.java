package DGU_AI_LAB.admin_be.domain.requests.controller;

import DGU_AI_LAB.admin_be.domain.requests.controller.docs.AdminRequestApi;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.ApproveRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.MigratePodRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.RejectRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.ContainerInfoDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.MigratePodResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.ResourceUsageDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.SaveRequestResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.service.AdminRequestCommandService;
import DGU_AI_LAB.admin_be.domain.requests.service.AdminRequestQueryService;
import DGU_AI_LAB.admin_be.domain.requests.service.OperationJobService;
import DGU_AI_LAB.admin_be.domain.requests.service.PodMigrationService;
import DGU_AI_LAB.admin_be.global.common.SuccessResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/requests")
public class AdminRequestController implements AdminRequestApi {

    private final AdminRequestCommandService adminRequestCommandService;
    private final AdminRequestQueryService adminRequestQueryService;
    private final PodMigrationService podMigrationService;
    private final OperationJobService operationJobService;


    /**
     * 모든 요청 목록 조회 (관리자용)
     * 모든 상태의 Request 목록을 반환합니다.
     */
    @GetMapping
    public ResponseEntity<SuccessResponse<?>> getAllRequests() {
        List<SaveRequestResponseDTO> requests = adminRequestQueryService.getAllRequests();
        return SuccessResponse.ok(requests);
    }

    @GetMapping("/usage")
    public ResponseEntity<SuccessResponse<?>> getAllResourceUsage() {
        List<ResourceUsageDTO> usage = adminRequestQueryService.getAllFulfilledResourceUsage();
        return SuccessResponse.ok(usage);
    }

    @GetMapping("/containers")
    public ResponseEntity<SuccessResponse<?>> getAllActiveContainers() {
        List<ContainerInfoDTO> containers = adminRequestQueryService.getAllActiveContainers();
        return SuccessResponse.ok(containers);
    }


    @PatchMapping("/approve")
    public ResponseEntity<SuccessResponse<?>> approveRequest(@RequestBody @Valid ApproveRequestDTO dto) {
        SaveRequestResponseDTO responseDto = adminRequestCommandService.approveRequest(dto);
        return SuccessResponse.ok(responseDto);
    }

    @PatchMapping("/reject")
    public ResponseEntity<SuccessResponse<?>> rejectRequest(@RequestBody @Valid RejectRequestDTO dto) {
        SaveRequestResponseDTO responseDto = adminRequestCommandService.rejectRequest(dto);
        return SuccessResponse.ok(responseDto);
    }

    /**
     * 신청의 생성·회수 작업 단계 기록 (신청 상세 화면용). 접근 시험 근거에 내부 정보가 섞일 수 있어
     * 인증 없이 config-server로 넘어가는 화면 경로(/pod-status/)가 아니라 이 관리자 API로만 준다.
     */
    @GetMapping("/{requestId}/job-steps")
    public ResponseEntity<SuccessResponse<?>> getJobSteps(@PathVariable Long requestId) {
        // 신청 조회 트랜잭션을 config-server 호출 동안 붙잡지 않도록 생성 시각만 먼저 받는다.
        return SuccessResponse.ok(operationJobService.getJobHistory(
                requestId, adminRequestQueryService.getRequestCreatedAt(requestId)));
    }

    @PostMapping("/{requestId}/migrate")
    public ResponseEntity<SuccessResponse<?>> migratePod(@PathVariable Long requestId, @RequestBody @Valid MigratePodRequestDTO dto) {
        MigratePodResponseDTO responseDto = podMigrationService.migratePod(requestId, dto);
        return SuccessResponse.ok(responseDto);
    }
}
