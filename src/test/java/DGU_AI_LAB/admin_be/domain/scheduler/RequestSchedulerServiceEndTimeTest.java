package DGU_AI_LAB.admin_be.domain.scheduler;

import DGU_AI_LAB.admin_be.domain.alarm.service.AlarmService;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.global.util.MessageUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RequestNotificationService - 만료 조회 종료 시각 범위 검증")
class RequestSchedulerServiceEndTimeTest {

    @InjectMocks
    private RequestNotificationService schedulerService;

    @Mock
    private RequestRepository requestRepository;

    @Mock
    private AlarmService alarmService;

    @Mock
    private MessageUtils messageUtils;

    @Test
    @DisplayName("종료 시각이 LocalTime.MAX(23:59:59.999999999)로 설정되어야 한다")
    void sendPreExpiryNotification_endTime_isLocalTimeMax() {
        LocalDateTime targetDate = LocalDateTime.of(2026, 12, 31, 10, 30, 0);
        LocalDateTime expectedStart = targetDate.toLocalDate().atStartOfDay();
        LocalDateTime expectedEnd = targetDate.toLocalDate().atTime(LocalTime.MAX);

        ArgumentCaptor<LocalDateTime> startCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> endCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<Status> statusCaptor = ArgumentCaptor.forClass(Status.class);

        when(requestRepository.findAllByExpiresAtBetweenAndStatus(
                startCaptor.capture(), endCaptor.capture(), statusCaptor.capture()))
                .thenReturn(Collections.emptyList());

        schedulerService.sendPreExpiryNotification(targetDate, "7일");

        assertThat(endCaptor.getValue()).isEqualTo(expectedEnd);
        assertThat(endCaptor.getValue().toLocalTime()).isEqualTo(LocalTime.MAX);
        assertThat(endCaptor.getValue().getNano()).isEqualTo(999_999_999);
        assertThat(startCaptor.getValue()).isEqualTo(expectedStart);
    }

    @Test
    @DisplayName("23:59:59.999 만료 요청이 조회 범위에 포함되어야 한다")
    void sendPreExpiryNotification_includesRequestExpiringAt_23_59_59_999() {
        LocalDateTime targetDate = LocalDateTime.of(2026, 12, 31, 10, 30, 0);
        LocalDateTime expiresAt = LocalDateTime.of(2026, 12, 31, 23, 59, 59, 999_000_000);

        LocalDateTime start = targetDate.toLocalDate().atStartOfDay();
        LocalDateTime end = targetDate.toLocalDate().atTime(LocalTime.MAX);

        assertThat(expiresAt.isAfter(start) || expiresAt.isEqual(start)).isTrue();
        assertThat(expiresAt.isBefore(end) || expiresAt.isEqual(end)).isTrue();
    }

    @Test
    @DisplayName("23:59:59.000 만료 요청이 조회 범위에 포함되어야 한다")
    void sendPreExpiryNotification_includesRequestExpiringAt_23_59_59_000() {
        LocalDateTime targetDate = LocalDateTime.of(2026, 12, 31, 10, 30, 0);
        LocalDateTime expiresAt = LocalDateTime.of(2026, 12, 31, 23, 59, 59, 0);

        LocalDateTime start = targetDate.toLocalDate().atStartOfDay();
        LocalDateTime end = targetDate.toLocalDate().atTime(LocalTime.MAX);

        assertThat(expiresAt.isAfter(start) || expiresAt.isEqual(start)).isTrue();
        assertThat(expiresAt.isBefore(end) || expiresAt.isEqual(end)).isTrue();
    }

    @Test
    @DisplayName("다음날 00:00:00 만료 요청은 조회 범위에 포함되지 않아야 한다")
    void sendPreExpiryNotification_excludesRequestExpiringAt_nextDay_00_00_00() {
        LocalDateTime targetDate = LocalDateTime.of(2026, 12, 31, 10, 30, 0);
        LocalDateTime expiresAt = LocalDateTime.of(2027, 1, 1, 0, 0, 0);

        LocalDateTime end = targetDate.toLocalDate().atTime(LocalTime.MAX);

        assertThat(expiresAt.isAfter(end)).isTrue();
    }
}
