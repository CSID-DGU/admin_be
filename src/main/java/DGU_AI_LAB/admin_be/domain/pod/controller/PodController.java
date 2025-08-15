package DGU_AI_LAB.admin_be.domain.pod.controller;

import DGU_AI_LAB.admin_be.domain.pod.controller.docs.PodApi;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import DGU_AI_LAB.admin_be.domain.pod.dto.response.PodResponseDTO;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/pods")
public class PodController implements PodApi {

    private final KubernetesClient client;

    // 전체 pod 목록 조회
    @GetMapping
    public List<String> getPods() {
        return client.pods()
                .inNamespace("default")
                .list()
                .getItems()
                .stream()
                .map(pod -> pod.getMetadata().getName())
                .collect(Collectors.toList());
    }

    // 단일 pod 정보 조회
    @GetMapping("/{podName}")
    public PodResponseDTO getPodDetail(@PathVariable String podName) {
        Pod pod = client.pods()
                .inNamespace("default")
                .withName(podName)
                .get();

        if (pod == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        return PodResponseDTO.fromEntity(pod);
    }
}