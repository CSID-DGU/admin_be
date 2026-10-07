package DGU_AI_LAB.admin_be.domain.scheduler;

import DGU_AI_LAB.admin_be.domain.alarm.service.AlarmService;
import DGU_AI_LAB.admin_be.domain.pod.dto.response.StoppedPodDTO;
import DGU_AI_LAB.admin_be.domain.pod.service.PodQueryService;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.global.alert.AlertDeduplicator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 승인 완료된 신청의 컨테이너가 멈추면(메모리 초과 등) 관리자에게 알린다. 사용자 Pod는 스스로 다시 뜨지 않아,
 * 알리지 않으면 관리자 화면을 직접 열기 전까지 아무도 모른다.
 *
 * <p>알림은 Pod마다 한 번이다. 재시작하면 Pod 이름이 바뀌므로 새 컨테이너가 또 멈추면 다시 알린다.
 * Pod가 아예 없는 신청은 다루지 않는다 — 기록과 실제의 어긋남 판정은 이 서비스의 몫이 아니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContainerStopWatcher {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("MM/dd HH:mm");
    private static final Map<String, String> REASON_LABELS = Map.of(
            "OOMKilled", "메모리 초과(OOMKilled)",
            "Evicted", "노드에서 밀려남(Evicted)");

    private final PodQueryService podQueryService;
    private final RequestRepository requestRepository;
    private final AlertDeduplicator alertDeduplicator;
    private final AlarmService alarmService;

    @Scheduled(fixedDelayString = "${containers.stop-watch.poll-ms:300000}")
    public void watchStoppedContainers() {
        List<StoppedPodDTO> stopped;
        try {
            stopped = podQueryService.listStoppedPods();
        } catch (Exception e) {
            // 조회 실패는 PodQueryService가 남긴다. 다음 바퀴에 다시 본다.
            return;
        }
        if (stopped.isEmpty()) {
            return;
        }
        Map<String, Request> requestByPod = requestRepository
                .findAllByStatusInWithAssociations(List.of(Status.FULFILLED)).stream()
                .filter(request -> request.getPodName() != null)
                .collect(Collectors.toMap(Request::getPodName, Function.identity(), (first, second) -> first));
        for (StoppedPodDTO pod : stopped) {
            Request request = requestByPod.get(pod.name());
            if (request != null) {
                alertOnce(request, pod);
            }
        }
    }

    private void alertOnce(Request request, StoppedPodDTO pod) {
        if (!alertDeduplicator.firstOccurrenceWhileItLasts(
                "container-stopped:" + request.getRequestId() + ":" + pod.name())) {
            return;
        }
        log.warn("컨테이너가 멈춤: requestId={}, pod={}, reason={}, exitCode={}",
                request.getRequestId(), pod.name(), pod.reason(), pod.exitCode());
        alarmService.alertNeedsAction("notification.admin.container.stopped",
                request.getRequestId(), request.getUbuntuUsername(),
                request.getResourceGroup().getServerName(),
                REASON_LABELS.getOrDefault(pod.reason(), pod.reason()),
                pod.exitCode() != null ? pod.exitCode().toString() : "-",
                formatTime(pod.finishedAt()),
                pod.nodeName() != null ? pod.nodeName() : "-", pod.name());
    }

    private static String formatTime(String finishedAt) {
        if (finishedAt == null) {
            return "-";
        }
        try {
            return OffsetDateTime.parse(finishedAt).atZoneSameInstant(ZONE).format(TIME_FORMAT);
        } catch (Exception e) {
            return finishedAt;
        }
    }
}
