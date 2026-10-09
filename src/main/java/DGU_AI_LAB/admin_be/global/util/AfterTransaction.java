package DGU_AI_LAB.admin_be.global.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 트랜잭션이 끝난 뒤에(커밋이든 롤백이든) 할 일을 미룬다. 요청을 거절하면서 그 사실을 알리는 경우처럼, 롤백되어도
 * 해야 하지만 전송이 느릴 때 행 잠금과 DB 연결을 쥐고 있으면 안 되는 일에 쓴다. 커밋됐을 때만 해야 하는 일은
 * {@link AfterCommit}을 쓴다. 트랜잭션이 없으면 바로 실행한다.
 *
 * <p>미룬 일이 실패해도 예외를 올리지 않고 기록만 한다.
 */
@Slf4j
public final class AfterTransaction {

    private AfterTransaction() {
    }

    /** @param what 실패했을 때 기록에 남길, 무엇을 하려던 일인지 */
    public static void run(String what, Runnable task) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            runSafely(what, task);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                runSafely(what, task);
            }
        });
    }

    private static void runSafely(String what, Runnable task) {
        try {
            task.run();
        } catch (Exception e) {
            log.error("트랜잭션 뒤 작업 실패({})", what, e);
        }
    }
}
