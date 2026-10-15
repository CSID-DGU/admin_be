package DGU_AI_LAB.admin_be.domain.pod.service;

import DGU_AI_LAB.admin_be.domain.pod.dto.response.PodResponseDTO;
import DGU_AI_LAB.admin_be.domain.pod.dto.response.StoppedPodDTO;
import DGU_AI_LAB.admin_be.domain.pod.dto.response.UserPodListing;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 어느 한 시각에 k8s에서 읽은 사용자 Pod 상태.
 *
 * @param checkedAt 읽은 시각. 아직 한 번도 읽지 못했으면 null
 */
public record PodStatusSnapshot(Map<String, PodResponseDTO> pods, List<StoppedPodDTO> stopped, LocalDateTime checkedAt) {

    public static final PodStatusSnapshot EMPTY = new PodStatusSnapshot(Map.of(), List.of(), null);

    public static PodStatusSnapshot of(UserPodListing listing, LocalDateTime checkedAt) {
        Map<String, PodResponseDTO> byName = listing.pods().stream()
                .collect(Collectors.toUnmodifiableMap(PodResponseDTO::name, Function.identity(), (first, second) -> first));
        return new PodStatusSnapshot(byName, List.copyOf(listing.stopped()), checkedAt);
    }

    /** 이 시각에 그 이름의 Pod가 없었으면(또는 이름이 없으면) null. */
    public PodResponseDTO pod(String podName) {
        return podName == null ? null : pods.get(podName);
    }
}
