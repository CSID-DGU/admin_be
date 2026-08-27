package DGU_AI_LAB.admin_be.domain.pod.service;

import DGU_AI_LAB.admin_be.domain.pod.dto.response.PodResponseDTO;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PodQueryService {

    private static final String NAMESPACE = "ailab-infra";

    private final KubernetesClient client;

    public List<String> getPodNames() {
        return client.pods()
                .inNamespace(NAMESPACE)
                .list()
                .getItems()
                .stream()
                .map(pod -> pod.getMetadata().getName())
                .collect(Collectors.toList());
    }

    public PodResponseDTO getPodDetail(String podName) {
        Pod pod = client.pods()
                .inNamespace(NAMESPACE)
                .withName(podName)
                .get();

        if (pod == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        return PodResponseDTO.fromEntity(pod);
    }
}
