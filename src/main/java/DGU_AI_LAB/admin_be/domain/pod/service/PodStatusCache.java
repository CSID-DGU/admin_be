package DGU_AI_LAB.admin_be.domain.pod.service;

import DGU_AI_LAB.admin_be.domain.pod.dto.response.UserPodListing;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 관리자 화면이 보여 주는 사용자 Pod 상태. 화면을 열 때마다 Pod마다 k8s를 부르지 않고, 주기 작업
 * ({@code ContainerStopWatcher})이 한 번에 읽어 둔 값을 돌려준다. k8s가 원본이라 DB에는 남기지 않는다.
 */
@Component
public class PodStatusCache {

    /** 수동 새로고침을 연달아 눌러도 이 간격 안에서는 k8s를 다시 읽지 않는다. */
    static final Duration MANUAL_REFRESH_MIN_INTERVAL = Duration.ofSeconds(10);

    private final PodQueryService podQueryService;
    private final Clock clock;
    private final AtomicReference<PodStatusSnapshot> current = new AtomicReference<>(PodStatusSnapshot.EMPTY);

    @Autowired
    public PodStatusCache(PodQueryService podQueryService) {
        this(podQueryService, Clock.system(ZoneId.of("Asia/Seoul")));
    }

    PodStatusCache(PodQueryService podQueryService, Clock clock) {
        this.podQueryService = podQueryService;
        this.clock = clock;
    }

    public PodStatusSnapshot snapshot() {
        return current.get();
    }

    /** k8s를 다시 읽어 바꾼다. 읽지 못하면 이전 값을(읽은 시각째로) 그대로 두고 던진다. */
    public synchronized PodStatusSnapshot refresh() {
        UserPodListing listing = podQueryService.listUserPods();
        PodStatusSnapshot next = PodStatusSnapshot.of(listing, LocalDateTime.now(clock));
        current.set(next);
        return next;
    }

    /** 관리자의 수동 새로고침. 방금 읽었으면 그 값을 돌려준다. */
    public synchronized PodStatusSnapshot refreshUnlessFresh() {
        PodStatusSnapshot snapshot = current.get();
        if (snapshot.checkedAt() != null
                && snapshot.checkedAt().isAfter(LocalDateTime.now(clock).minus(MANUAL_REFRESH_MIN_INTERVAL))) {
            return snapshot;
        }
        return refresh();
    }
}
