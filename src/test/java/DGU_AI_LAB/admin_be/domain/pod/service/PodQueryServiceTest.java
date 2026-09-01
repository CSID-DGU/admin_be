package DGU_AI_LAB.admin_be.domain.pod.service;

import DGU_AI_LAB.admin_be.domain.pod.dto.response.PodEventDTO;
import DGU_AI_LAB.admin_be.domain.pod.dto.response.PodResponseDTO;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.EventBuilder;
import io.fabric8.kubernetes.api.model.EventList;
import io.fabric8.kubernetes.api.model.EventListBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.PodListBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.ContainerResource;
import io.fabric8.kubernetes.client.dsl.FilterWatchListDeletable;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.PodResource;
import io.fabric8.kubernetes.client.dsl.PrettyLoggable;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.V1APIGroupDSL;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PodQueryService")
class PodQueryServiceTest {

    @Mock private KubernetesClient client;
    @Mock private MixedOperation<Pod, PodList, PodResource> pods;
    @Mock private NonNamespaceOperation<Pod, PodList, PodResource> inNamespace;
    @Mock private PodResource podResource;
    @Mock private PrettyLoggable prettyLoggable;
    @Mock private ContainerResource containerResource;
    @Mock private V1APIGroupDSL v1;
    @Mock private MixedOperation<Event, EventList, Resource<Event>> eventsOp;
    @Mock private NonNamespaceOperation<Event, EventList, Resource<Event>> eventsInNamespace;
    @Mock private FilterWatchListDeletable<Event, EventList, Resource<Event>> eventsFiltered;

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

    @Nested
    @DisplayName("getPodLogs")
    class GetPodLogs {

        @Test
        @DisplayName("container를 지정하지 않으면 첫 번째 컨테이너 기준으로 최근 500줄을 반환한다")
        void returnsLogs_whenContainerOmitted() {
            when(inNamespace.withName("pod-a")).thenReturn(podResource);
            when(podResource.get()).thenReturn(podNamed("pod-a"));
            when(podResource.tailingLines(500)).thenReturn(prettyLoggable);
            when(prettyLoggable.getLog()).thenReturn("line1\nline2");

            String result = podQueryService.getPodLogs("pod-a", null);

            assertThat(result).isEqualTo("line1\nline2");
        }

        @Test
        @DisplayName("container를 지정하면 해당 컨테이너의 로그를 반환한다")
        void returnsLogs_forSpecifiedContainer() {
            when(inNamespace.withName("pod-a")).thenReturn(podResource);
            when(podResource.get()).thenReturn(podNamed("pod-a"));
            when(podResource.inContainer("jupyter")).thenReturn(containerResource);
            when(containerResource.tailingLines(500)).thenReturn(prettyLoggable);
            when(prettyLoggable.getLog()).thenReturn("jupyter log");

            String result = podQueryService.getPodLogs("pod-a", "jupyter");

            assertThat(result).isEqualTo("jupyter log");
        }

        @Test
        @DisplayName("Pod가 존재하지 않으면 RESOURCE_NOT_FOUND BusinessException을 던진다")
        void throwsBusinessException_whenPodNotFound() {
            when(inNamespace.withName("missing-pod")).thenReturn(podResource);
            when(podResource.get()).thenReturn(null);

            assertThatThrownBy(() -> podQueryService.getPodLogs("missing-pod", null))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        }

        @Test
        @DisplayName("컨테이너가 아직 시작 전이라 로그 조회 자체가 실패하면 빈 문자열을 반환한다")
        void returnsEmptyString_whenLogFetchFails() {
            when(inNamespace.withName("pod-a")).thenReturn(podResource);
            when(podResource.get()).thenReturn(podNamed("pod-a"));
            when(podResource.tailingLines(anyInt())).thenThrow(new RuntimeException("container is not running"));

            String result = podQueryService.getPodLogs("pod-a", null);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getPodEvents")
    class GetPodEvents {

        @Test
        @DisplayName("involvedObject.name으로 필터링해 최신순으로 정렬된 이벤트를 반환한다")
        void returnsEventsSortedByLatestFirst() {
            Event older = new EventBuilder()
                    .withType("Normal").withReason("Scheduled").withMessage("스케줄됨")
                    .withLastTimestamp("2026-09-01T09:00:00Z").withCount(1)
                    .build();
            Event newer = new EventBuilder()
                    .withType("Warning").withReason("FailedScheduling").withMessage("0/5 nodes available")
                    .withLastTimestamp("2026-09-01T10:00:00Z").withCount(3)
                    .build();
            EventList eventList = new EventListBuilder().withItems(older, newer).build();

            when(client.v1()).thenReturn(v1);
            when(v1.events()).thenReturn(eventsOp);
            when(eventsOp.inNamespace("ailab-infra")).thenReturn(eventsInNamespace);
            when(eventsInNamespace.withField("involvedObject.name", "pod-a")).thenReturn(eventsFiltered);
            when(eventsFiltered.list()).thenReturn(eventList);

            List<PodEventDTO> result = podQueryService.getPodEvents("pod-a");

            assertThat(result).extracting(PodEventDTO::reason).containsExactly("FailedScheduling", "Scheduled");
            assertThat(result.get(0).count()).isEqualTo(3);
        }
    }
}
