package DGU_AI_LAB.admin_be.domain.pod.service;

import DGU_AI_LAB.admin_be.domain.pod.dto.response.PodResponseDTO;
import DGU_AI_LAB.admin_be.domain.pod.dto.response.StoppedPodDTO;
import DGU_AI_LAB.admin_be.domain.pod.dto.response.UserPodListing;
import DGU_AI_LAB.admin_be.global.cluster.ClusterId;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 여러 클러스터에서 각각 읽은 사용자 Pod 상태. Pod 이름은 클러스터 안에서만 유일하다. */
public record PodStatusSnapshot(
        Map<PodKey, PodResponseDTO> pods,
        List<ClusterStoppedPod> stopped,
        Map<String, LocalDateTime> checkedAtByCluster
) {

    public static final PodStatusSnapshot EMPTY = new PodStatusSnapshot(Map.of(), List.of(), Map.of());

    public PodStatusSnapshot {
        pods = Map.copyOf(pods);
        stopped = List.copyOf(stopped);
        checkedAtByCluster = Map.copyOf(checkedAtByCluster);
    }

    /** 한 클러스터에서 새로 읽은 값만 교체하고 다른 클러스터의 마지막 성공값은 유지한다. */
    public PodStatusSnapshot withCluster(String requestedClusterId, UserPodListing listing, LocalDateTime checkedAt) {
        String clusterId = ClusterId.canonical(requestedClusterId);
        Map<PodKey, PodResponseDTO> nextPods = new LinkedHashMap<>(pods);
        nextPods.keySet().removeIf(key -> key.clusterId().equals(clusterId));
        nextPods.putAll(listing.pods().stream().collect(Collectors.toMap(
                pod -> new PodKey(clusterId, pod.name()), Function.identity(), (first, second) -> first)));

        List<ClusterStoppedPod> nextStopped = new java.util.ArrayList<>(stopped);
        nextStopped.removeIf(item -> item.clusterId().equals(clusterId));
        listing.stopped().stream().map(pod -> new ClusterStoppedPod(clusterId, pod)).forEach(nextStopped::add);

        Map<String, LocalDateTime> nextChecked = new LinkedHashMap<>(checkedAtByCluster);
        nextChecked.put(clusterId, checkedAt);
        return new PodStatusSnapshot(nextPods, nextStopped, nextChecked);
    }

    public PodResponseDTO pod(String clusterId, String podName) {
        return clusterId == null || clusterId.isBlank() || podName == null
                ? null
                : pods.get(new PodKey(clusterId, podName));
    }

    public LocalDateTime checkedAt(String clusterId) {
        return clusterId == null || clusterId.isBlank()
                ? null
                : checkedAtByCluster.get(ClusterId.canonical(clusterId));
    }

    /** 전체 화면에는 가장 오래된 클러스터의 확인 시각을 보여 과도하게 최신처럼 보이지 않게 한다. */
    public LocalDateTime checkedAt() {
        return checkedAtByCluster.values().stream().min(LocalDateTime::compareTo).orElse(null);
    }

    public record PodKey(String clusterId, String podName) {
        public PodKey {
            clusterId = ClusterId.canonical(clusterId);
            if (podName == null || podName.isBlank()) {
                throw new IllegalArgumentException("podName은 비어 있을 수 없습니다.");
            }
        }
    }

    public record ClusterStoppedPod(String clusterId, StoppedPodDTO pod) {
        public ClusterStoppedPod {
            clusterId = ClusterId.canonical(clusterId);
        }
    }
}
