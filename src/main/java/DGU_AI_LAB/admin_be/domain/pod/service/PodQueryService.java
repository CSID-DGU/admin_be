package DGU_AI_LAB.admin_be.domain.pod.service;

import DGU_AI_LAB.admin_be.domain.pod.dto.response.PodEventDTO;
import DGU_AI_LAB.admin_be.domain.pod.dto.response.PodResponseDTO;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.PodResource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * K8s API 호출은 RBAC 권한 누락(403) 등으로 조용히 실패하기 쉬운데, 아무 데도 로그가
 * 안 남으면 프론트에 "—"나 빈 값만 보이고 원인을 알 방법이 없다 (실제로 events/pods/log
 * 권한 누락을 이런 식으로 겪었다). 그래서 각 메서드가 실패를 그대로 던지되, 어떤 조회가
 * 무엇 때문에 실패했는지는 여기서 남긴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PodQueryService {

    private static final String NAMESPACE = "ailab-infra";
    private static final int LOG_TAIL_LINES = 500;
    private static final int EVENT_LIMIT = 50;

    private final KubernetesClient client;

    public List<String> getPodNames() {
        try {
            return client.pods()
                    .inNamespace(NAMESPACE)
                    .list()
                    .getItems()
                    .stream()
                    .map(pod -> pod.getMetadata().getName())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("[PodQuery] pod 목록 조회 실패", e);
            throw e;
        }
    }

    public PodResponseDTO getPodDetail(String podName) {
        Pod pod;
        try {
            pod = client.pods()
                    .inNamespace(NAMESPACE)
                    .withName(podName)
                    .get();
        } catch (Exception e) {
            log.error("[PodQuery] pod 상세 조회 실패: podName={}", podName, e);
            throw e;
        }

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
        Pod pod;
        try {
            pod = podResource.get();
        } catch (Exception e) {
            log.error("[PodQuery] 로그 조회 대상 pod 확인 실패: podName={}", podName, e);
            throw e;
        }
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
            // 오류가 아니라 "아직 로그가 없다"는 정상 상황이므로 빈 문자열로 처리한다. 다만 RBAC
            // 권한 누락(pods/log 403) 등 진짜 오류도 같은 catch로 들어와 여태 조용히 묻혔으므로,
            // 동작은 그대로 두되(빈 문자열 반환) 최소한 로그는 남긴다.
            log.warn("[PodQuery] pod 로그 조회 실패, 빈 문자열로 처리: podName={}, container={}",
                    podName, containerName, e);
            return "";
        }
    }

    public List<PodEventDTO> getPodEvents(String podName) {
        try {
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
        } catch (Exception e) {
            log.error("[PodQuery] pod 이벤트 조회 실패: podName={}", podName, e);
            throw e;
        }
    }
}
