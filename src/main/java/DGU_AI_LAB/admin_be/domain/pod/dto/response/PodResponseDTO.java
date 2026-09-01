package DGU_AI_LAB.admin_be.domain.pod.dto.response;

import io.fabric8.kubernetes.api.model.ContainerState;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodCondition;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Schema(description = "Kubernetes Pod 조회 응답 DTO")
@Builder
public record PodResponseDTO(
        @Schema(description = "Pod 이름") String name,
        @Schema(description = "네임스페이스") String namespace,
        @Schema(description = "Pod phase (Running / Pending 등)") String status,
        @Schema(description = "실제 노출용 상태 — 컨테이너가 CrashLoopBackOff/ImagePullBackOff 등으로 " +
                "대기 중이면 phase(대개 Running/Pending)보다 이 값을 우선해서 보여준다") String effectiveStatus,
        @Schema(description = "effectiveStatus가 phase와 다를 때 그 사유 메시지") String reason,
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
            @Schema(description = "컨테이너 이미지") String image,
            @Schema(description = "Ready 여부") Boolean ready,
            @Schema(description = "재시작 횟수") Integer restartCount,
            @Schema(description = "waiting/running/terminated 중 현재 상태") String state,
            @Schema(description = "state가 waiting/terminated일 때의 사유 (CrashLoopBackOff, ImagePullBackOff 등)") String reason,
            @Schema(description = "reason에 대한 상세 메시지") String message
    ) {
    }

    @Schema(description = "볼륨 정보")
    @Builder
    public record VolumeDTO(
            @Schema(description = "볼륨 이름") String name
    ) {
    }

    // 컨테이너 상태의 waiting/terminated reason 중 이 목록에 있는 값은 phase보다 우선해
    // effectiveStatus로 노출한다 — CrashLoopBackOff 등은 phase가 여전히 Running으로 남아있어
    // phase만 보면 실제로 반복 재시작 중인 컨테이너를 놓친다.
    private static final List<String> PRIORITY_REASONS = List.of(
            "CrashLoopBackOff", "ImagePullBackOff", "ErrImagePull", "OOMKilled", "Evicted", "Error"
    );

    public static PodResponseDTO fromEntity(Pod pod) {
        List<VolumeDTO> volumes = pod.getSpec() != null && pod.getSpec().getVolumes() != null
                ? pod.getSpec().getVolumes().stream()
                .map(volume -> VolumeDTO.builder()
                        .name(volume.getName())
                        .build())
                .collect(Collectors.toList())
                : List.of();

        Map<String, ContainerStatus> statusByName = pod.getStatus() != null && pod.getStatus().getContainerStatuses() != null
                ? pod.getStatus().getContainerStatuses().stream()
                        .collect(Collectors.toMap(ContainerStatus::getName, cs -> cs, (a, b) -> a))
                : Map.of();

        List<ContainerDTO> containers = pod.getSpec() != null && pod.getSpec().getContainers() != null
                ? pod.getSpec().getContainers().stream()
                .map(container -> {
                    ContainerStatus cs = statusByName.get(container.getName());
                    StateInfo state = extractState(cs);
                    return ContainerDTO.builder()
                            .name(container.getName())
                            .image(container.getImage())
                            .ready(cs != null ? cs.getReady() : null)
                            .restartCount(cs != null ? cs.getRestartCount() : null)
                            .state(state.name())
                            .reason(state.reason())
                            .message(state.message())
                            .build();
                })
                .collect(Collectors.toList())
                : List.of();

        String hostIP = pod.getStatus() != null ? pod.getStatus().getHostIP() : null;
        String nodeName = pod.getSpec() != null ? pod.getSpec().getNodeName() : null;
        String phase = pod.getStatus() != null ? pod.getStatus().getPhase() : "Unknown";

        // Pod 레벨 reason(Evicted 등)이나 컨테이너 waiting/terminated reason 중 우선순위
        // 목록에 있는 값을 phase보다 우선한다.
        String podLevelReason = pod.getStatus() != null ? pod.getStatus().getReason() : null;
        Optional<StateInfo> priorityContainerState = containers.stream()
                .map(c -> new StateInfo(c.state(), c.reason(), c.message()))
                .filter(s -> s.reason() != null && PRIORITY_REASONS.contains(s.reason()))
                .findFirst();

        String effectiveStatus;
        String reason;
        if (podLevelReason != null && PRIORITY_REASONS.contains(podLevelReason)) {
            effectiveStatus = podLevelReason;
            reason = pod.getStatus().getMessage();
        } else if (priorityContainerState.isPresent()) {
            effectiveStatus = priorityContainerState.get().reason();
            reason = priorityContainerState.get().message();
        } else {
            effectiveStatus = phase;
            reason = null;
        }

        // 스케줄 자체가 안 되는 경우(FailedScheduling 등)는 PodCondition에만 실려온다 —
        // farm9 disk-pressure taint처럼 노드 스케줄링 실패로 Pending에 갇힌 케이스를 노출한다.
        if ("Pending".equals(phase) && reason == null) {
            reason = findUnscheduledReason(pod.getStatus() != null ? pod.getStatus().getConditions() : null);
        }

        return PodResponseDTO.builder()
                .name(pod.getMetadata().getName())
                .namespace(pod.getMetadata().getNamespace())
                .status(phase)
                .effectiveStatus(effectiveStatus)
                .reason(reason)
                .creationTimestamp(pod.getMetadata().getCreationTimestamp())
                .labels(pod.getMetadata().getLabels())
                .annotations(pod.getMetadata().getAnnotations())
                .containers(containers)
                .volumes(volumes)
                .hostIP(hostIP)
                .nodeName(nodeName)
                .build();
    }

    private record StateInfo(String name, String reason, String message) {}

    private static StateInfo extractState(ContainerStatus cs) {
        if (cs == null || cs.getState() == null) {
            return new StateInfo(null, null, null);
        }
        ContainerState state = cs.getState();
        if (state.getWaiting() != null) {
            return new StateInfo("waiting", state.getWaiting().getReason(), state.getWaiting().getMessage());
        }
        if (state.getTerminated() != null) {
            return new StateInfo("terminated", state.getTerminated().getReason(), state.getTerminated().getMessage());
        }
        if (state.getRunning() != null) {
            return new StateInfo("running", null, null);
        }
        return new StateInfo(null, null, null);
    }

    private static String findUnscheduledReason(List<PodCondition> conditions) {
        if (conditions == null) {
            return null;
        }
        return conditions.stream()
                .filter(c -> "PodScheduled".equals(c.getType()) && "False".equals(c.getStatus()))
                .map(PodCondition::getMessage)
                .findFirst()
                .orElse(null);
    }
}