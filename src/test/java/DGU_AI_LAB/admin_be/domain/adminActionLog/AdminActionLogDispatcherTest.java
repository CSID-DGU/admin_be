package DGU_AI_LAB.admin_be.domain.adminActionLog;

import DGU_AI_LAB.admin_be.domain.adminActionLog.dto.AdminActionEvent;
import DGU_AI_LAB.admin_be.domain.adminActionLog.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.dao.TransientDataAccessResourceException;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AdminActionLogDispatcherTest {
    final AdminActionEvent event = new AdminActionEvent("event", LocalDateTime.now(), 7L,
            AdminActionType.REQUEST_APPROVE, AdminTargetType.REQUEST, "5", 202, null, "ABSENT", "{}");

    @Test
    void retriesTransientFailuresWithSameEvent() throws Exception {
        var service = mock(AdminActionLogCommandService.class);
        var completed = new CountDownLatch(1);
        doThrow(new TransientDataAccessResourceException("offline"))
                .doThrow(new TransientDataAccessResourceException("offline"))
                .doAnswer(invocation -> { completed.countDown(); return null; }).when(service).save(event);
        var dispatcher = new AdminActionLogDispatcher(service);
        try {
            dispatcher.submit(event);
            assertThat(completed.await(5, TimeUnit.SECONDS)).isTrue();
            verify(service, times(3)).save(event);
        } finally { dispatcher.shutdown(); }
    }

    @Test
    void slowStorageDoesNotBlockRequestAndShutdownRejectionDoesNotThrow() throws Exception {
        var service = mock(AdminActionLogCommandService.class);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        doAnswer(invocation -> { entered.countDown(); release.await(5, TimeUnit.SECONDS); return null; }).when(service).save(event);
        var dispatcher = new AdminActionLogDispatcher(service);
        try {
            dispatcher.submit(event);
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            // 저장이 아직 끝나지 않아도 submit은 이미 반환했다.
            assertThat(release.getCount()).isEqualTo(1);
        } finally { release.countDown(); dispatcher.shutdown(); }
        dispatcher.submit(event);
        verify(service, times(1)).save(event);
    }
}
