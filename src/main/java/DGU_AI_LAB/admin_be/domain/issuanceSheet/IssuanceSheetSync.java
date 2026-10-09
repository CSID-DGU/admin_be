package DGU_AI_LAB.admin_be.domain.issuanceSheet;

import DGU_AI_LAB.admin_be.global.event.RequestRowChangedEvent;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 컨테이너 발급 내역을 서버별 탭에 통째로 옮겨 적는다. 주기적으로 돌지 않고, 신청·포트 행이 바뀐 커밋 뒤와 서버가
 * 뜬 직후에만 DB의 현재 상태를 읽어 옮긴다. 옮기지 못한 탭이 있으면 잠시 뒤 한 번 더 한다(성공할 때까지).
 *
 * <p>전용 스레드 하나에서 돈다 — 요청을 처리하던 스레드가 Google 응답을 기다리지 않고, 몰려 온 변경은 한 번으로 합쳐진다.
 * 내용이 직전에 옮겨 적은 것과 같으면 호출하지 않는다. 그래서 탭을 사람이 손으로 고친 것은 발급 내역이 바뀌거나
 * 서버가 다시 뜰 때 덮어쓴다.
 */
@Slf4j
@Component
@EnableConfigurationProperties(IssuanceSheetProperties.class)
public class IssuanceSheetSync {

    private final IssuanceSheetProperties properties;
    private final IssuanceSheetQueryService queryService;
    private final SheetTabWriter writer;
    private final long retryMs;

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "issuance-sheet-sync");
        thread.setDaemon(true);
        return thread;
    });
    /** 아직 시작하지 않은 실행이 줄에 있는지. 있으면 새 변경은 그 실행이 함께 옮긴다. */
    private final AtomicBoolean queued = new AtomicBoolean();
    private final Map<String, List<List<String>>> lastWritten = new HashMap<>();

    public IssuanceSheetSync(IssuanceSheetProperties properties, IssuanceSheetQueryService queryService,
                             SheetTabWriter writer, @Value("${issuance-sheet.retry-ms:60000}") long retryMs) {
        this.properties = properties;
        this.queryService = queryService;
        this.writer = writer;
        this.retryMs = retryMs;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onRequestRowChanged(RequestRowChangedEvent event) {
        requestSync(0);
    }

    /** 꺼져 있던 동안의 변경과 직전 실행에서 옮기지 못한 것을 맞춘다. */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        requestSync(0);
    }

    private void requestSync(long delayMs) {
        if (!properties.active()) {
            return;
        }
        if (queued.compareAndSet(false, true)) {
            executor.schedule(this::run, delayMs, TimeUnit.MILLISECONDS);
        }
    }

    private void run() {
        // 읽기 전에 내린다 — 읽는 도중 커밋된 변경은 새 실행을 줄에 세운다.
        queued.set(false);
        try {
            if (!syncNow()) {
                requestSync(retryMs);
            }
        } catch (RuntimeException e) {
            log.error("발급 내역 시트: 갱신 중 예기치 못한 오류", e);
        }
    }

    /** @return 모든 탭이 DB와 같아졌으면 true */
    boolean syncNow() {
        Map<String, List<List<String>>> tables;
        try {
            tables = queryService.rowsByServer();
        } catch (Exception e) {
            log.warn("발급 내역 시트: 내역 조회 실패. 잠시 뒤 다시 한다.", e);
            return false;
        }
        boolean allWritten = true;
        for (Map.Entry<String, List<List<String>>> table : tables.entrySet()) {
            allWritten &= writeIfChanged(properties.tabName(table.getKey()), table.getValue());
        }
        return allWritten;
    }

    private boolean writeIfChanged(String tabName, List<List<String>> rows) {
        if (rows.equals(lastWritten.get(tabName))) {
            return true;
        }
        try {
            writer.replaceTab(tabName, rows);
            lastWritten.put(tabName, rows);
            log.info("발급 내역 시트: '{}' 탭 갱신({}건)", tabName, rows.size() - 1);
            return true;
        } catch (Exception e) {
            // 탭이 일부만 바뀌었을 수 있으니 같은 내용이라도 다시 쓴다.
            lastWritten.remove(tabName);
            log.warn("발급 내역 시트: '{}' 탭 갱신 실패. 잠시 뒤 다시 한다. 원인: {}", tabName, e.toString());
            return false;
        }
    }

    @PreDestroy
    void stop() {
        executor.shutdownNow();
    }
}
