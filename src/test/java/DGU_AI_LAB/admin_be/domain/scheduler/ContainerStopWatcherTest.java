package DGU_AI_LAB.admin_be.domain.scheduler;

import DGU_AI_LAB.admin_be.domain.alarm.service.AlarmService;
import DGU_AI_LAB.admin_be.domain.pod.dto.response.StoppedPodDTO;
import DGU_AI_LAB.admin_be.domain.pod.service.PodQueryService;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup;
import DGU_AI_LAB.admin_be.global.alert.InMemoryAlertDeduplicator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ContainerStopWatcher")
class ContainerStopWatcherTest {

    private static final String KEY = "notification.admin.container.stopped";
    private static final StoppedPodDTO OOM =
            new StoppedPodDTO("ailab-a-1", "farm9", "OOMKilled", 137, "2026-10-07T00:24:14Z");

    @Mock private PodQueryService podQueryService;
    @Mock private RequestRepository requestRepository;
    @Mock private AlarmService alarmService;

    private ContainerStopWatcher watcher;

    @BeforeEach
    void setUp() {
        watcher = new ContainerStopWatcher(podQueryService, requestRepository,
                new InMemoryAlertDeduplicator(), alarmService);
    }

    private void fulfilled(long requestId, String podName) {
        ResourceGroup group = mock(ResourceGroup.class);
        when(group.getServerName()).thenReturn("FARM");
        Request request = mock(Request.class);
        when(request.getRequestId()).thenReturn(requestId);
        when(request.getPodName()).thenReturn(podName);
        when(request.getUbuntuUsername()).thenReturn("a");
        when(request.getResourceGroup()).thenReturn(group);
        when(requestRepository.findAllByStatusInWithAssociations(List.of(Status.FULFILLED)))
                .thenReturn(List.of(request));
    }

    @Test
    @DisplayName("승인 완료된 신청의 컨테이너가 멈추면 사유·종료 코드·한국 시각·위치를 담아 알린다")
    void alertsStoppedContainer() {
        fulfilled(7L, "ailab-a-1");
        when(podQueryService.listStoppedPods()).thenReturn(List.of(OOM));

        watcher.watchStoppedContainers();

        verify(alarmService).alertNeedsAction(KEY, 7L, "a", "FARM",
                "메모리 초과(OOMKilled)", "137", "10/07 09:24", "farm9", "ailab-a-1");
    }

    @Test
    @DisplayName("같은 Pod는 계속 멈춰 있어도 한 번만 알린다")
    void alertsOncePerPod() {
        fulfilled(7L, "ailab-a-1");
        when(podQueryService.listStoppedPods()).thenReturn(List.of(OOM));

        watcher.watchStoppedContainers();
        watcher.watchStoppedContainers();

        verify(alarmService, times(1)).alertNeedsAction(any(), any(Object[].class));
    }

    @Test
    @DisplayName("재시작으로 새로 만든 Pod가 또 멈추면 다시 알린다")
    void alertsAgainForNewPod() {
        fulfilled(7L, "ailab-a-1");
        when(podQueryService.listStoppedPods()).thenReturn(List.of(OOM));
        watcher.watchStoppedContainers();

        fulfilled(7L, "ailab-a-2");
        when(podQueryService.listStoppedPods()).thenReturn(List.of(
                new StoppedPodDTO("ailab-a-2", "farm9", "Error", 1, null)));
        watcher.watchStoppedContainers();

        verify(alarmService, times(2)).alertNeedsAction(any(), any(Object[].class));
        verify(alarmService).alertNeedsAction(KEY, 7L, "a", "FARM", "Error", "1", "-", "farm9", "ailab-a-2");
    }

    @Test
    @DisplayName("승인 완료된 신청의 Pod가 아니면 알리지 않는다")
    void ignoresPodsWithoutFulfilledRequest() {
        fulfilled(7L, "ailab-other");
        when(podQueryService.listStoppedPods()).thenReturn(List.of(OOM));

        watcher.watchStoppedContainers();

        verify(alarmService, never()).alertNeedsAction(any(), any(Object[].class));
    }

    @Test
    @DisplayName("멈춘 Pod가 없으면 신청을 읽지 않는다")
    void skipsDatabaseWhenNothingStopped() {
        when(podQueryService.listStoppedPods()).thenReturn(List.of());

        watcher.watchStoppedContainers();

        verifyNoInteractions(requestRepository, alarmService);
    }

    @Test
    @DisplayName("Pod 조회가 실패해도 예외를 밖으로 내지 않는다")
    void survivesKubernetesFailure() {
        when(podQueryService.listStoppedPods()).thenThrow(new RuntimeException("403"));

        watcher.watchStoppedContainers();

        verifyNoInteractions(requestRepository, alarmService);
    }
}
