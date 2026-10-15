package DGU_AI_LAB.admin_be.domain.pod.service;

import DGU_AI_LAB.admin_be.domain.pod.dto.response.PodResponseDTO;
import DGU_AI_LAB.admin_be.domain.pod.dto.response.StoppedPodDTO;
import DGU_AI_LAB.admin_be.domain.pod.dto.response.UserPodListing;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PodStatusCache")
class PodStatusCacheTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Instant T0 = LocalDateTime.of(2026, 10, 15, 21, 25).atZone(SEOUL).toInstant();
    private static final PodResponseDTO RUNNING = PodResponseDTO.builder().name("ailab-a-1").status("Running").build();
    private static final StoppedPodDTO OOM = new StoppedPodDTO("ailab-b-1", "farm9", "OOMKilled", 137, null);

    @Mock private PodQueryService podQueryService;

    private MutableClock clock;
    private PodStatusCache cache;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(T0);
        cache = new PodStatusCache(podQueryService, clock);
    }

    @Test
    @DisplayName("한 번도 읽기 전에는 빈 값과 읽은 시각 없음(null)을 돌려주고 k8s를 부르지 않는다")
    void emptyBeforeFirstRefresh() {
        PodStatusSnapshot snapshot = cache.snapshot();

        assertThat(snapshot.pods()).isEmpty();
        assertThat(snapshot.checkedAt()).isNull();
        assertThat(snapshot.pod("ailab-a-1")).isNull();
        verify(podQueryService, times(0)).listUserPods();
    }

    @Test
    @DisplayName("읽은 Pod를 이름으로 찾을 수 있게 담고 한국 시각으로 읽은 시각을 남긴다")
    void refreshStoresListing() {
        when(podQueryService.listUserPods()).thenReturn(new UserPodListing(List.of(RUNNING), List.of(OOM)));

        cache.refresh();

        PodStatusSnapshot snapshot = cache.snapshot();
        assertThat(snapshot.pod("ailab-a-1")).isEqualTo(RUNNING);
        assertThat(snapshot.pod(null)).isNull();
        assertThat(snapshot.stopped()).containsExactly(OOM);
        assertThat(snapshot.checkedAt()).isEqualTo(LocalDateTime.of(2026, 10, 15, 21, 25));
    }

    @Test
    @DisplayName("읽기에 실패하면 이전 값과 그 시각을 그대로 두고 예외를 던진다")
    void keepsPreviousOnFailure() {
        when(podQueryService.listUserPods())
                .thenReturn(new UserPodListing(List.of(RUNNING), List.of()))
                .thenThrow(new RuntimeException("403"));
        cache.refresh();
        clock.advance(Duration.ofMinutes(5));

        assertThatThrownBy(cache::refresh).hasMessage("403");
        assertThat(cache.snapshot().pod("ailab-a-1")).isEqualTo(RUNNING);
        assertThat(cache.snapshot().checkedAt()).isEqualTo(LocalDateTime.of(2026, 10, 15, 21, 25));
    }

    @Test
    @DisplayName("수동 새로고침은 10초 안에 다시 부르면 k8s를 다시 읽지 않고, 지나면 다시 읽는다")
    void manualRefreshIsThrottled() {
        when(podQueryService.listUserPods()).thenReturn(new UserPodListing(List.of(RUNNING), List.of()));

        cache.refreshUnlessFresh();
        clock.advance(Duration.ofSeconds(9));
        cache.refreshUnlessFresh();
        verify(podQueryService, times(1)).listUserPods();

        clock.advance(Duration.ofSeconds(2));
        PodStatusSnapshot snapshot = cache.refreshUnlessFresh();
        verify(podQueryService, times(2)).listUserPods();
        assertThat(snapshot.checkedAt()).isEqualTo(LocalDateTime.of(2026, 10, 15, 21, 25, 11));
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return SEOUL;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
