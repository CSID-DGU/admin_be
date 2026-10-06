package DGU_AI_LAB.admin_be.global.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 커밋된 뒤에 할 일(알림 전송 등)을 미룬다. 트랜잭션 안에서 보내면 전송이 느릴 때 행 잠금과 DB 연결을 그만큼 쥐고,
 * 롤백되면 일어나지 않은 일을 알리게 된다. 트랜잭션이 없으면 바로 실행한다.
 *
 * <p>미룬 일이 실패해도 예외를 올리지 않고 기록만 한다 — 이미 커밋된 처리를 호출자에게 실패로 보이게 하지 않는다.
 */
@Slf4j
public final class AfterCommit {

    private AfterCommit() {
    }

    /** @param what 실패했을 때 기록에 남길, 무엇을 하려던 일인지 */
    public static void run(String what, Runnable task) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            runSafely(what, task);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                runSafely(what, task);
            }
        });
    }

    private static void runSafely(String what, Runnable task) {
        try {
            task.run();
        } catch (Exception e) {
            log.error("커밋 뒤 작업 실패({}). 앞선 처리는 정상적으로 반영되었습니다.", what, e);
        }
    }
}
