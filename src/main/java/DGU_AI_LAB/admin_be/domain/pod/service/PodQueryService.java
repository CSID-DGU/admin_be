package DGU_AI_LAB.admin_be.domain.pod.service;

import DGU_AI_LAB.admin_be.domain.pod.dto.response.PodEventDTO;
import DGU_AI_LAB.admin_be.domain.pod.dto.response.PodResponseDTO;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.PodResource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PodQueryService {

    private static final String NAMESPACE = "ailab-infra";
    private static final int LOG_TAIL_LINES = 500;
    private static final int EVENT_LIMIT = 50;

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

    /**
     * @param containerName 생략하면(null) 첫 번째 컨테이너의 로그를 반환한다.
     */
    public String getPodLogs(String podName, String containerName) {
        PodResource podResource = client.pods().inNamespace(NAMESPACE).withName(podName);
        Pod pod = podResource.get();
        if (pod == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        try {
            if (containerName != null && !containerName.isBlank()) {
                return podResource.inContainer(containerName).tailingLines(LOG_TAIL_LINES).getLog();
            }
            return podResource.tailingLines(LOG_TAIL_LINES).getLog();
        } catch (Exception e) {
            // 컨테이너가 아직 시작 전(ContainerCreating 등)이면 K8s API가 로그 조회 자체를 거부한다 —
            // 오류가 아니라 "아직 로그가 없다"는 정상 상황이므로 빈 문자열로 처리한다.
            return "";
        }
    }

    public List<PodEventDTO> getPodEvents(String podName) {
        return client.v1().events()
                .inNamespace(NAMESPACE)
                .withField("involvedObject.name", podName)
                .list()
                .getItems()
                .stream()
                .map(PodEventDTO::fromEntity)
                .sorted(Comparator.comparing(PodEventDTO::lastTimestamp, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(EVENT_LIMIT)
                .collect(Collectors.toList());
    }
}
