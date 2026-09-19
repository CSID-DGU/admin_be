package DGU_AI_LAB.admin_be.domain.scheduler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 만료 배치 주기와 정체 복구 주기와 정체 임계는 실험 스택에서 짧게 가속해야 하므로 설정값으로 빠져 있다.
 * 이 시험이 보는 것은 값이 맞는지가 아니라, 설정을 주입하지 않았을 때의 기본값이 현행 운영 값과 같다는 사실이다.
 * 실험용 가속이 기본값 자리로 새어 들어오면 baseline 의 측정 조건이 조용히 바뀌므로, 그때 이 시험이 먼저 실패한다.
 * 근거는 docs/adr/ADR-006-baseline-recovery-timers.md 의 결정 1 이다.
 */
@DisplayName("RequestSchedulerService - 스케줄러 타이머 기본값 고정")
class SchedulerTimerDefaultsTest {

    private static final String THRESHOLD_FIELD = "staleInFlightThresholdMinutes";

    @Test
    @DisplayName("만료 배치 cron 의 기본값이 매일 08시(Asia/Seoul)다")
    void expiryCron_defaultsToDailyEightAM() throws Exception {
        Scheduled scheduled = RequestSchedulerService.class
                .getDeclaredMethod("runScheduler").getAnnotation(Scheduled.class);

        assertThat(scheduled.cron()).isEqualTo("${scheduler.expiry-cron:0 00 08 * * ?}");
        assertThat(scheduled.zone()).isEqualTo("Asia/Seoul");
    }

    @Test
    @DisplayName("정체 복구 주기의 기본값이 5분(300000ms)이다")
    void reconcileRate_defaultsToFiveMinutes() throws Exception {
        Scheduled scheduled = RequestSchedulerService.class
                .getDeclaredMethod("reconcileStaleInFlightRequests").getAnnotation(Scheduled.class);

        assertThat(scheduled.fixedRateString()).isEqualTo("${scheduler.reconcile-rate-ms:300000}");
    }

    @Test
    @DisplayName("정체 임계 자리표시자의 기본값이 20분이다")
    void staleThresholdPlaceholder_defaultsToTwentyMinutes() throws Exception {
        Field field = RequestSchedulerService.class.getDeclaredField(THRESHOLD_FIELD);

        assertThat(field.getAnnotation(Value.class).value())
                .isEqualTo("${scheduler.stale-threshold-minutes:20}");
    }

    @Test
    @DisplayName("설정을 주입하지 않은 인스턴스의 정체 임계가 20분이다")
    void staleThresholdField_initialisesToTwentyMinutes() throws Exception {
        RequestSchedulerService service = new RequestSchedulerService(
                null, null, null, null, null, null, null);

        Field field = RequestSchedulerService.class.getDeclaredField(THRESHOLD_FIELD);
        field.setAccessible(true);

        assertThat(field.getLong(service)).isEqualTo(20L);
    }
}
