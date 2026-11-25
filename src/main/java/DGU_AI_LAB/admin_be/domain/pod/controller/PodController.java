package DGU_AI_LAB.admin_be.domain.pod.controller;

import DGU_AI_LAB.admin_be.domain.adminActionLog.AdminAction;
import DGU_AI_LAB.admin_be.domain.adminActionLog.AdminActionType;
import DGU_AI_LAB.admin_be.domain.pod.controller.docs.PodApi;
import DGU_AI_LAB.admin_be.domain.pod.dto.response.PodEventDTO;
import DGU_AI_LAB.admin_be.domain.pod.dto.response.PodResponseDTO;
import DGU_AI_LAB.admin_be.domain.pod.service.PodQueryService;
import DGU_AI_LAB.admin_be.domain.pod.service.PodStatusCache;
import DGU_AI_LAB.admin_be.domain.requests.service.PodService;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import DGU_AI_LAB.admin_be.global.cluster.ClusterId;
import DGU_AI_LAB.admin_be.global.common.SuccessResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class PodController implements PodApi {

    /** 클러스터 경로 도입 전 API는 FARM만 관리했다. 전환 기간 동안 같은 의미로 유지한다. */
    private static final String LEGACY_CLUSTER_ID = "FARM";

    private final PodQueryService podQueryService;
    private final PodService podService;
    private final PodStatusCache podStatusCache;

    // 전체 pod 목록 조회
    @GetMapping({"/api/admin/clusters/{clusterId}/pods", "/api/admin/pods"})
    public List<String> getPods(@PathVariable(required = false) String clusterId) {
        return podQueryService.getPodNames(clusterOrLegacy(clusterId));
    }

    // 관리자 화면의 컨테이너 상태를 지금 다시 읽는다 (주기 갱신을 기다리지 않을 때)
    @PostMapping({"/api/admin/clusters/{clusterId}/pods/status-refresh", "/api/admin/pods/status-refresh"})
    public ResponseEntity<SuccessResponse<?>> refreshPodStatus(@PathVariable(required = false) String clusterId) {
        String selected = clusterOrLegacy(clusterId);
        return SuccessResponse.ok(Map.of("statusCheckedAt", podStatusCache.refreshUnlessFresh(selected).checkedAt(selected)));
    }

    // 단일 pod 정보 조회
    @GetMapping({"/api/admin/clusters/{clusterId}/pods/{podName}", "/api/admin/pods/{podName}"})
    public PodResponseDTO getPodDetail(
            @PathVariable(required = false) String clusterId, @PathVariable String podName) {
        return podQueryService.getPodDetail(clusterOrLegacy(clusterId), podName);
    }

    // pod 로그 조회 (최근 500줄)
    @GetMapping({"/api/admin/clusters/{clusterId}/pods/{podName}/logs", "/api/admin/pods/{podName}/logs"})
    public Map<String, String> getPodLogs(
            @PathVariable(required = false) String clusterId,
            @PathVariable String podName,
            @RequestParam(required = false) String container
    ) {
        return Map.of("logs", podQueryService.getPodLogs(clusterOrLegacy(clusterId), podName, container));
    }

    // pod 이벤트 조회 (최신 50건)
    @GetMapping({"/api/admin/clusters/{clusterId}/pods/{podName}/events", "/api/admin/pods/{podName}/events"})
    public List<PodEventDTO> getPodEvents(
            @PathVariable(required = false) String clusterId, @PathVariable String podName) {
        return podQueryService.getPodEvents(clusterOrLegacy(clusterId), podName);
    }

    // DB에 대응하는 신청이 없는 고아 Pod 직접 삭제 (신청 이력이 있으면 409로 거부)
    @AdminAction(value = AdminActionType.ORPHAN_POD_DELETE, targetId = "podName")
    @DeleteMapping({"/api/admin/clusters/{clusterId}/pods/{podName}", "/api/admin/pods/{podName}"})
    public ResponseEntity<SuccessResponse<?>> deleteOrphanPod(
            @PathVariable(required = false) String clusterId, @PathVariable String podName) {
        podService.deleteOrphanPod(clusterOrLegacy(clusterId), podName);
        return SuccessResponse.ok(null);
    }

    private static String clusterOrLegacy(String clusterId) {
        if (clusterId == null) {
            return LEGACY_CLUSTER_ID;
        }
        try {
            return ClusterId.canonical(clusterId);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }
}
