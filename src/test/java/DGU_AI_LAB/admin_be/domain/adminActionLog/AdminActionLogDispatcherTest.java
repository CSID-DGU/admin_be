package DGU_AI_LAB.admin_be.domain.adminActionLog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import DGU_AI_LAB.admin_be.domain.adminActionLog.dto.AdminActionEvent;
import DGU_AI_LAB.admin_be.domain.adminActionLog.service.AdminActionLogCommandService;
import DGU_AI_LAB.admin_be.domain.adminActionLog.service.AdminActionLogDispatcher;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.TransientDataAccessResourceException;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@DisplayName("관리자 작업 기록 비동기 저장")
class AdminActionLogDispatcherTest {
    final AdminActionEvent event =
            new AdminActionEvent(
                    "event",
                    LocalDateTime.now(),
                    7L,
                    AdminActionType.REQUEST_APPROVE,
                    AdminTargetType.REQUEST,
                    "5",
                    202,
                    null,
                    "ABSENT",
                    "{}");

    @Test
    @DisplayName("일시적 저장 실패는 같은 이벤트로 제한적으로 재시도한다")
    void retriesTransientFailuresWithSameEvent() throws Exception {
        var service = mock(AdminActionLogCommandService.class);
        var completed = new CountDownLatch(1);
        doThrow(new TransientDataAccessResourceException("offline"))
                .doThrow(new TransientDataAccessResourceException("offline"))
                .doAnswer(
                        invocation -> {
                            completed.countDown();
                            return null;
                        })
                .when(service)
                .save(event);
        var dispatcher = new AdminActionLogDispatcher(service);
        try {
            dispatcher.submit(event);
            assertThat(completed.await(5, TimeUnit.SECONDS)).isTrue();
            verify(service, times(3)).save(event);
        } finally {
            dispatcher.shutdown();
        }
    }

    @Test
    @DisplayName("저장 대기가 요청을 막지 않고 종료 후 제출 거부도 예외를 전파하지 않는다")
    void slowStorageDoesNotBlockRequestAndShutdownRejectionDoesNotThrow() throws Exception {
        var service = mock(AdminActionLogCommandService.class);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        doAnswer(
                        invocation -> {
                            entered.countDown();
                            release.await(5, TimeUnit.SECONDS);
                            return null;
                        })
                .when(service)
                .save(event);
        var dispatcher = new AdminActionLogDispatcher(service);
        try {
            dispatcher.submit(event);
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            // 저장이 아직 끝나지 않아도 submit은 이미 반환했다.
            assertThat(release.getCount()).isEqualTo(1);
        } finally {
            release.countDown();
            dispatcher.shutdown();
        }
        dispatcher.submit(event);
        verify(service, times(1)).save(event);
    }
}
