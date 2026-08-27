package DGU_AI_LAB.admin_be.domain.pod.service;

import DGU_AI_LAB.admin_be.domain.pod.dto.response.PodResponseDTO;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.PodListBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.PodResource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PodQueryService")
class PodQueryServiceTest {

    @Mock private KubernetesClient client;
    @Mock private MixedOperation<Pod, PodList, PodResource> pods;
    @Mock private NonNamespaceOperation<Pod, PodList, PodResource> inNamespace;
    @Mock private PodResource podResource;

    private PodQueryService podQueryService;

    @BeforeEach
    void setUp() {
        podQueryService = new PodQueryService(client);
        when(client.pods()).thenReturn(pods);
        when(pods.inNamespace("ailab-infra")).thenReturn(inNamespace);
    }

    private Pod podNamed(String name) {
        return new PodBuilder()
                .withMetadata(new ObjectMetaBuilder().withName(name).build())
                .build();
    }

    @Nested
    @DisplayName("getPodNames")
    class GetPodNames {

        @Test
        @DisplayName("ailab-infra 네임스페이스의 Pod 이름 목록을 반환한다")
        void returnsPodNames_whenPodsExist() {
            Pod pod1 = podNamed("pod-a");
            Pod pod2 = podNamed("pod-b");
            PodList podList = new PodListBuilder().withItems(pod1, pod2).build();
            when(inNamespace.list()).thenReturn(podList);

            List<String> result = podQueryService.getPodNames();

            assertThat(result).containsExactly("pod-a", "pod-b");
        }

        @Test
        @DisplayName("Pod가 하나도 없으면 빈 리스트를 반환한다")
        void returnsEmptyList_whenNoPodsExist() {
            PodList emptyList = new PodListBuilder().withItems(List.of()).build();
            when(inNamespace.list()).thenReturn(emptyList);

            List<String> result = podQueryService.getPodNames();

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getPodDetail")
    class GetPodDetail {

        @Test
        @DisplayName("Pod가 존재하면 PodResponseDTO로 변환해 반환한다")
        void returnsMappedDto_whenPodExists() {
            when(inNamespace.withName("pod-a")).thenReturn(podResource);
            when(podResource.get()).thenReturn(podNamed("pod-a"));

            PodResponseDTO result = podQueryService.getPodDetail("pod-a");

            assertThat(result.name()).isEqualTo("pod-a");
        }

        @Test
        @DisplayName("Pod가 존재하지 않으면 RESOURCE_NOT_FOUND BusinessException을 던진다")
        void throwsBusinessException_whenPodNotFound() {
            when(inNamespace.withName("missing-pod")).thenReturn(podResource);
            when(podResource.get()).thenReturn(null);

            assertThatThrownBy(() -> podQueryService.getPodDetail("missing-pod"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }
}
