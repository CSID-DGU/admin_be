package DGU_AI_LAB.admin_be.domain.pod.dto.response;

import io.fabric8.kubernetes.api.model.Pod;
import lombok.Builder;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Builder
public record PodResponseDTO(
        String name,
        String namespace,
        String status,
        String creationTimestamp,
        Map<String, String> labels,
        Map<String, String> annotations,
        List<ContainerDTO> containers,
        List<VolumeDTO> volumes,
        String hostIP,
        String nodeName
) {

    @Builder
    public record ContainerDTO(
            String name,
            String image
    ) {}

    @Builder
    public record VolumeDTO(
            String name
    ) {}

    public static PodResponseDTO fromEntity(Pod pod) {
        List<VolumeDTO> volumes = pod.getSpec() != null && pod.getSpec().getVolumes() != null
                ? pod.getSpec().getVolumes().stream()
                .map(volume -> VolumeDTO.builder()
                        .name(volume.getName())
                        .build())
                .collect(Collectors.toList())
                : List.of();

        List<ContainerDTO> containers = pod.getSpec() != null && pod.getSpec().getContainers() != null
                ? pod.getSpec().getContainers().stream()
                .map(container -> ContainerDTO.builder()
                        .name(container.getName())
                        .image(container.getImage())
                        .build())
                .collect(Collectors.toList())
                : List.of();

        String hostIP = pod.getStatus() != null ? pod.getStatus().getHostIP() : null;
        String nodeName = pod.getSpec() != null ? pod.getSpec().getNodeName() : null;

        return PodResponseDTO.builder()
                .name(pod.getMetadata().getName())
                .namespace(pod.getMetadata().getNamespace())
                .status(pod.getStatus() != null ? pod.getStatus().getPhase() : "Unknown")
                .creationTimestamp(pod.getMetadata().getCreationTimestamp())
                .labels(pod.getMetadata().getLabels())
                .annotations(pod.getMetadata().getAnnotations())
                .containers(containers)
                .volumes(volumes)
                .hostIP(hostIP)
                .nodeName(nodeName)
                .build();
    }
}