package DGU_AI_LAB.admin_be.domain.pod.controller;

import DGU_AI_LAB.admin_be.domain.pod.controller.docs.PodApi;
import DGU_AI_LAB.admin_be.domain.pod.dto.response.PodEventDTO;
import DGU_AI_LAB.admin_be.domain.pod.dto.response.PodResponseDTO;
import DGU_AI_LAB.admin_be.domain.pod.service.PodQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/pods")
public class PodController implements PodApi {

    private final PodQueryService podQueryService;

    // 전체 pod 목록 조회
    @GetMapping
    public List<String> getPods() {
        return podQueryService.getPodNames();
    }

    // 단일 pod 정보 조회
    @GetMapping("/{podName}")
    public PodResponseDTO getPodDetail(@PathVariable String podName) {
        return podQueryService.getPodDetail(podName);
    }

    // pod 로그 조회 (최근 500줄)
    @GetMapping("/{podName}/logs")
    public Map<String, String> getPodLogs(
            @PathVariable String podName,
            @RequestParam(required = false) String container
    ) {
        return Map.of("logs", podQueryService.getPodLogs(podName, container));
    }

    // pod 이벤트 조회 (최신 50건)
    @GetMapping("/{podName}/events")
    public List<PodEventDTO> getPodEvents(@PathVariable String podName) {
        return podQueryService.getPodEvents(podName);
    }
}