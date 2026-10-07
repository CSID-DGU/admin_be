package DGU_AI_LAB.admin_be.domain.pod.dto.response;

import io.fabric8.kubernetes.api.model.ContainerStateTerminated;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodStatus;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 멈춰서 스스로 돌아오지 않는 Pod. 사용자 Pod는 재시작 정책이 Never라 Failed/Succeeded에 들어가면
 * 누가 다시 만들기 전까지 그대로 남는다.
 *
 * @param reason     멈춘 사유(OOMKilled, Evicted, Error 등). 알 수 없으면 Pod 단계
 * @param exitCode   컨테이너 종료 코드. 컨테이너가 뜨기 전에 멈췄으면 null
 * @param finishedAt 컨테이너 종료 시각(RFC 3339). 없으면 null
 */
public record StoppedPodDTO(String name, String nodeName, String reason, Integer exitCode, String finishedAt) {

    private static final Set<String> STOPPED_PHASES = Set.of("Failed", "Succeeded");

    public static Optional<StoppedPodDTO> from(Pod pod) {
        PodStatus status = pod.getStatus();
        if (status == null || !STOPPED_PHASES.contains(status.getPhase())) {
            return Optional.empty();
        }
        Optional<ContainerStateTerminated> terminated = Optional.ofNullable(status.getContainerStatuses())
                .orElse(List.of()).stream()
                .map(ContainerStatus::getState)
                .filter(Objects::nonNull)
                .map(state -> state.getTerminated())
                .filter(Objects::nonNull)
                .findFirst();
        // 축출(Evicted)처럼 Pod 전체에 붙는 사유가 컨테이너 사유보다 원인에 가깝다.
        String reason = status.getReason() != null ? status.getReason()
                : terminated.map(ContainerStateTerminated::getReason).orElse(status.getPhase());
        return Optional.of(new StoppedPodDTO(
                pod.getMetadata().getName(),
                pod.getSpec() != null ? pod.getSpec().getNodeName() : null,
                reason,
                terminated.map(ContainerStateTerminated::getExitCode).orElse(null),
                terminated.map(ContainerStateTerminated::getFinishedAt).orElse(null)));
    }
}
