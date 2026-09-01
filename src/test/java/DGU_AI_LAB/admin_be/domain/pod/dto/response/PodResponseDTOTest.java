package DGU_AI_LAB.admin_be.domain.pod.dto.response;

import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.ContainerStateBuilder;
import io.fabric8.kubernetes.api.model.ContainerStatusBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodConditionBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PodResponseDTO.fromEntity")
class PodResponseDTOTest {

    @Test
    @DisplayName("컨테이너가 CrashLoopBackOff로 대기 중이면, phase가 Running이어도 effectiveStatus는 CrashLoopBackOff다")
    void crashLoopBackOff_overridesRunningPhase() {
        Pod pod = new PodBuilder()
                .withMetadata(new ObjectMetaBuilder().withName("pod-a").build())
                .withNewSpec()
                    .addToContainers(new ContainerBuilder().withName("main").withImage("img:1").build())
                .endSpec()
                .withNewStatus()
                    .withPhase("Running")
                    .addToContainerStatuses(new ContainerStatusBuilder()
                            .withName("main")
                            .withReady(false)
                            .withRestartCount(7)
                            .withState(new ContainerStateBuilder()
                                    .withNewWaiting()
                                        .withReason("CrashLoopBackOff")
                                        .withMessage("back-off 5m0s restarting failed container")
                                    .endWaiting()
                                    .build())
                            .build())
                .endStatus()
                .build();

        PodResponseDTO result = PodResponseDTO.fromEntity(pod);

        assertThat(result.status()).isEqualTo("Running");
        assertThat(result.effectiveStatus()).isEqualTo("CrashLoopBackOff");
        assertThat(result.reason()).isEqualTo("back-off 5m0s restarting failed container");
        assertThat(result.containers()).hasSize(1);
        assertThat(result.containers().get(0).restartCount()).isEqualTo(7);
        assertThat(result.containers().get(0).ready()).isFalse();
        assertThat(result.containers().get(0).state()).isEqualTo("waiting");
    }

    @Test
    @DisplayName("모든 컨테이너가 정상 Running이면 effectiveStatus도 phase와 동일하게 Running이다")
    void allRunning_effectiveStatusMatchesPhase() {
        Pod pod = new PodBuilder()
                .withMetadata(new ObjectMetaBuilder().withName("pod-a").build())
                .withNewSpec()
                    .addToContainers(new ContainerBuilder().withName("main").withImage("img:1").build())
                .endSpec()
                .withNewStatus()
                    .withPhase("Running")
                    .addToContainerStatuses(new ContainerStatusBuilder()
                            .withName("main")
                            .withReady(true)
                            .withRestartCount(0)
                            .withState(new ContainerStateBuilder().withNewRunning().endRunning().build())
                            .build())
                .endStatus()
                .build();

        PodResponseDTO result = PodResponseDTO.fromEntity(pod);

        assertThat(result.effectiveStatus()).isEqualTo("Running");
        assertThat(result.reason()).isNull();
    }

    @Test
    @DisplayName("Pending이고 PodScheduled 컨디션이 False면, 스케줄 실패 메시지를 reason으로 노출한다")
    void pendingUnschedulable_exposesConditionMessage() {
        Pod pod = new PodBuilder()
                .withMetadata(new ObjectMetaBuilder().withName("pod-a").build())
                .withNewStatus()
                    .withPhase("Pending")
                    .addToConditions(new PodConditionBuilder()
                            .withType("PodScheduled")
                            .withStatus("False")
                            .withMessage("0/5 nodes are available: 1 node(s) had taint {node.kubernetes.io/disk-pressure: }")
                            .build())
                .endStatus()
                .build();

        PodResponseDTO result = PodResponseDTO.fromEntity(pod);

        assertThat(result.effectiveStatus()).isEqualTo("Pending");
        assertThat(result.reason()).contains("disk-pressure");
    }

    @Test
    @DisplayName("status/spec이 없는 최소 Pod도 NPE 없이 안전하게 변환된다")
    void minimalPod_doesNotThrow() {
        Pod pod = new PodBuilder()
                .withMetadata(new ObjectMetaBuilder().withName("pod-a").build())
                .build();

        PodResponseDTO result = PodResponseDTO.fromEntity(pod);

        assertThat(result.status()).isEqualTo("Unknown");
        assertThat(result.effectiveStatus()).isEqualTo("Unknown");
        assertThat(result.containers()).isEmpty();
    }
}
