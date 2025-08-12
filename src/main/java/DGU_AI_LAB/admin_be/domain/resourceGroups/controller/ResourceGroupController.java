package DGU_AI_LAB.admin_be.domain.resourceGroups.controller;

import DGU_AI_LAB.admin_be.domain.gpus.dto.response.GpuTypeResponseDTO;
import DGU_AI_LAB.admin_be.domain.resourceGroups.service.ResourceGroupService;
import DGU_AI_LAB.admin_be.global.common.SuccessResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/resources")
public class ResourceGroupController {

    private final ResourceGroupService resourceGroupService;

    /**
     * GPU 기종별(리소스그룹) 리소스 정보 조회 API
     * GET /api/dashboard/gpu-types
     *
     * 우선 모든 노드는 현재 사용 가능하다고 가정합니다.
     */
    @GetMapping("/gpu-types")
    public ResponseEntity<SuccessResponse<?>> getGpuTypeResources() {
        List<GpuTypeResponseDTO> gpuTypes = resourceGroupService.getGpuTypeResources();
        return SuccessResponse.ok(gpuTypes);
    }

}
