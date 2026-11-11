package DGU_AI_LAB.admin_be.domain.monitoring.controller;

import DGU_AI_LAB.admin_be.domain.monitoring.controller.docs.AdminGpuUsageApi;
import DGU_AI_LAB.admin_be.domain.monitoring.service.AdminGpuUsageService;
import DGU_AI_LAB.admin_be.global.common.SuccessResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 전용(/api/admin/** 은 SecurityConfig에서 ADMIN만 허용). */
@RestController
@RequiredArgsConstructor
public class AdminGpuUsageController implements AdminGpuUsageApi {

    private final AdminGpuUsageService adminGpuUsageService;

    @Override
    public ResponseEntity<SuccessResponse<?>> getGpuUsage(@RequestParam(name = "node", required = false) String node) {
        return SuccessResponse.ok(adminGpuUsageService.getGpuUsage(node));
    }
}
