package DGU_AI_LAB.admin_be.domain.requests.controller;

import DGU_AI_LAB.admin_be.domain.requests.dto.request.ApprovalRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.RejectionRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.ApproveRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.RejectRequestDTO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;

import DGU_AI_LAB.admin_be.domain.requests.controller.docs.AdminRequestApi;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.ApproveRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.MigratePodRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.RejectRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.ContainerInfoDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.ResourceUsageDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.SaveRequestResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.service.AdminRequestCommandService;
import DGU_AI_LAB.admin_be.domain.requests.service.AdminRequestQueryService;
import DGU_AI_LAB.admin_be.domain.requests.service.OperationJobService;
import DGU_AI_LAB.admin_be.domain.requests.service.PodMigrationService;
import DGU_AI_LAB.admin_be.domain.requests.service.RequestExpiryService;
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
    private final RequestExpiryService requestExpiryService;


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


    @PostMapping("/{requestId}/approval")
    public ResponseEntity<SuccessResponse<?>> approveRequest(@PathVariable Long requestId,
                                                             @RequestBody @Valid ApprovalRequestDTO dto) {
        SaveRequestResponseDTO responseDto = adminRequestCommandService.approveRequest(
                new ApproveRequestDTO(requestId, dto.imageId(), dto.resourceGroupId(), dto.adminComment()));
        return SuccessResponse.accepted(responseDto);
    }

    @PostMapping("/{requestId}/rejection")
    public ResponseEntity<SuccessResponse<?>> rejectRequest(@PathVariable Long requestId,
                                                            @RequestBody @Valid RejectionRequestDTO dto) {
        SaveRequestResponseDTO responseDto = adminRequestCommandService.rejectRequest(
                new RejectRequestDTO(requestId, dto.adminComment()));
        return SuccessResponse.ok(responseDto);
    }

    @GetMapping("/{requestId}/job-steps")
    public ResponseEntity<SuccessResponse<?>> getJobSteps(@PathVariable Long requestId) {
        // 신청 조회 트랜잭션을 config-server 호출 동안 붙잡지 않도록 생성 시각만 먼저 받는다.
        return SuccessResponse.ok(operationJobService.getJobHistory(
                requestId, adminRequestQueryService.getRequestCreatedAt(requestId)));
    }

    @PostMapping("/{requestId}/migrations")
    public ResponseEntity<SuccessResponse<?>> startMigration(@PathVariable Long requestId,
                                                             @RequestBody @Valid MigratePodRequestDTO dto) {
        podMigrationService.startMigration(requestId, dto);
        return SuccessResponse.accepted(null);
    }

    @GetMapping("/{requestId}/migrations/latest")
    public ResponseEntity<SuccessResponse<?>> getLatestMigration(@PathVariable Long requestId) {
        return SuccessResponse.ok(podMigrationService.getLatestMigration(requestId));
    }

    /**
     * 컨테이너 하나만 회수한다. 우분투 계정과 홈 디렉터리는 남고, 같은 사용자의 다른 컨테이너는
     * 건드리지 않는다. 계정까지 회수하려면 사용자 관리의 계정 회수(DELETE /api/admin/users/{id}/ubuntu-account)를
     * 쓴다 — 그쪽은 그 사용자의 컨테이너를 전부 정리한다.
     */
    @DeleteMapping("/{requestId}/container")
    public ResponseEntity<SuccessResponse<?>> deleteContainer(@PathVariable Long requestId) {
        requestExpiryService.deleteContainerByAdmin(requestId);
        return SuccessResponse.ok(null);
    }
}
