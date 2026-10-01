package DGU_AI_LAB.admin_be.domain.users.service;

import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import DGU_AI_LAB.admin_be.global.util.EmailService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class PasswordResetCodeMailerTest {

    @Mock private EmailService emailService;

    /** 맡은 일을 쌓아 두기만 하는 실행기 — 요청이 발송을 기다리지 않는지 볼 수 있다. */
    private static class QueueingExecutor extends AbstractExecutorService {
        final List<Runnable> queued = new ArrayList<>();
        boolean rejecting;

        @Override
        public void execute(Runnable command) {
            if (rejecting) {
                throw new RejectedExecutionException("full");
            }
            queued.add(command);
        }

        void runAll() {
            queued.forEach(Runnable::run);
        }

        @Override public void shutdown() { }
        @Override public List<Runnable> shutdownNow() { return List.of(); }
        @Override public boolean isShutdown() { return false; }
        @Override public boolean isTerminated() { return false; }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return true; }
    }

    private final QueueingExecutor executor = new QueueingExecutor();

    private PasswordResetCodeMailer mailer() {
        return new PasswordResetCodeMailer(emailService, (ExecutorService) executor);
    }

    @Test
    @DisplayName("발송을 맡기기만 하고 돌아온다 — 메일은 그 뒤에 나간다")
    void sendLater_doesNotSendInline() {
        mailer().sendLater("test@dgu.ac.kr", "Test@dgu.ac.kr");

        verifyNoInteractions(emailService);
        executor.runAll();
        verify(emailService).sendPasswordResetCode("test@dgu.ac.kr", "Test@dgu.ac.kr");
    }

    @Test
    @DisplayName("발송이 실패해도 예외를 밖으로 내지 않는다")
    void sendFailure_isSwallowed() {
        doThrow(new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR))
                .when(emailService).sendPasswordResetCode("test@dgu.ac.kr", "Test@dgu.ac.kr");
        mailer().sendLater("test@dgu.ac.kr", "Test@dgu.ac.kr");

        assertThatCode(executor::runAll).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("발송이 밀려 더 맡을 수 없어도 요청은 실패하지 않는다")
    void rejected_isSwallowed() {
        executor.rejecting = true;

        assertThatCode(() -> mailer().sendLater("test@dgu.ac.kr", "Test@dgu.ac.kr")).doesNotThrowAnyException();
        assertThat(executor.queued).isEmpty();
    }
}
