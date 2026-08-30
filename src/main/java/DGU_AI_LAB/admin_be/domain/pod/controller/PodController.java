package DGU_AI_LAB.admin_be.domain.pod.controller;

import DGU_AI_LAB.admin_be.domain.pod.controller.docs.PodApi;
import DGU_AI_LAB.admin_be.domain.pod.dto.response.PodResponseDTO;
import DGU_AI_LAB.admin_be.domain.pod.service.PodQueryService;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.PodCreationStatusResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.service.PodService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/pods")
public class PodController implements PodApi {

    private final PodQueryService podQueryService;
    private final PodService podService;

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

    // 사용자별 Pod 생성 진행 상태 조회
    @GetMapping("/status/{username}")
    public PodCreationStatusResponseDTO getPodCreationStatus(@PathVariable String username) {
        return podService.getPodCreationStatus(username);
    }
}