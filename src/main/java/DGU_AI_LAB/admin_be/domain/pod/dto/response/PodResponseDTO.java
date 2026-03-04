package DGU_AI_LAB.admin_be.domain.pod.dto.response;

import io.fabric8.kubernetes.api.model.Pod;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Schema(description = "Kubernetes Pod 조회 응답 DTO")
@Builder
public record PodResponseDTO(
        @Schema(description = "Pod 이름") String name,
        @Schema(description = "네임스페이스") String namespace,
        @Schema(description = "Pod 상태 (Running / Pending 등)") String status,
        @Schema(description = "생성 타임스탬프") String creationTimestamp,
        @Schema(description = "Pod를 식별하기 위한 Kubernetes 레이블") Map<String, String> labels,
        @Schema(description = "Pod에 설정된 Kubernetes 어노테이션") Map<String, String> annotations,
        @Schema(description = "Pod 내에서 실행 중인 컨테이너") List<ContainerDTO> containers,
        @Schema(description = "Pod에 마운트된 PVC/ConfigMap 등의 볼륨") List<VolumeDTO> volumes,
        @Schema(description = "호스트 IP") String hostIP,
        @Schema(description = "노드 이름") String nodeName
) {

    @Schema(description = "컨테이너 정보")
    @Builder
    public record ContainerDTO(
            @Schema(description = "컨테이너 이름") String name,
            @Schema(description = "컨테이너 이미지") String image
    ) {
    }

    @Schema(description = "볼륨 정보")
    @Builder
    public record VolumeDTO(
            @Schema(description = "볼륨 이름") String name
    ) {
    }

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