package DGU_AI_LAB.admin_be.global.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class AfterCommitTest {

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("트랜잭션이 없으면 바로 실행한다")
    void runsImmediatelyWithoutTransaction() {
        List<String> ran = new ArrayList<>();

        AfterCommit.run("테스트", () -> ran.add("done"));

        assertThat(ran).containsExactly("done");
    }

    @Test
    @DisplayName("트랜잭션 안에서는 커밋될 때까지 실행하지 않는다")
    void defersUntilCommit() {
        List<String> ran = new ArrayList<>();
        TransactionSynchronizationManager.initSynchronization();

        AfterCommit.run("테스트", () -> ran.add("done"));

        assertThat(ran).isEmpty();
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        assertThat(ran).containsExactly("done");
    }

    @Test
    @DisplayName("롤백되면 실행하지 않는다")
    void skippedOnRollback() {
        List<String> ran = new ArrayList<>();
        TransactionSynchronizationManager.initSynchronization();

        AfterCommit.run("테스트", () -> ran.add("done"));
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        assertThat(ran).isEmpty();
    }

    @Test
    @DisplayName("미룬 일이 실패해도 예외를 올리지 않는다")
    void failureDoesNotPropagate() {
        TransactionSynchronizationManager.initSynchronization();
        AfterCommit.run("테스트", () -> {
            throw new IllegalStateException("메일 서버 장애");
        });

        assertThatCode(() -> TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit)).doesNotThrowAnyException();
        assertThatCode(() -> AfterCommit.run("테스트", () -> {
            throw new IllegalStateException("메일 서버 장애");
        })).doesNotThrowAnyException();
    }
}
