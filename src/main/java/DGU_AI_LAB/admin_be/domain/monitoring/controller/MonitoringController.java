// Prometheus에서 서버별 GPU 사용량·활성 컨테이너를 조회하는 공개 모니터링 엔드포인트
package DGU_AI_LAB.admin_be.domain.monitoring.controller;

import DGU_AI_LAB.admin_be.domain.monitoring.service.MonitoringService;
import DGU_AI_LAB.admin_be.global.common.SuccessResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/monitoring")
@RequiredArgsConstructor
public class MonitoringController {

    private final MonitoringService monitoringService;

    @GetMapping("/metrics")
    public ResponseEntity<SuccessResponse<?>> getMetrics() {
        return SuccessResponse.ok(monitoringService.getMetrics());
    }
}
