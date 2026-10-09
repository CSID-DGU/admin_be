package DGU_AI_LAB.admin_be.domain.issuanceSheet;

import DGU_AI_LAB.admin_be.global.event.RequestRowChangedEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("IssuanceSheetSync")
class IssuanceSheetSyncTest {

    private static final List<List<String>> HEADER_ONLY = List.of(List.of("상태"));
    private static final List<List<String>> ONE_ROW = List.of(List.of("상태"), List.of("사용 중"));

    private final IssuanceSheetQueryService queryService = mock(IssuanceSheetQueryService.class);
    private final SheetTabWriter writer = mock(SheetTabWriter.class);
    private IssuanceSheetSync sync;

    private IssuanceSheetSync sync(String spreadsheetId) {
        return sync(true, spreadsheetId);
    }

    private IssuanceSheetSync sync(boolean enabled, String spreadsheetId) {
        sync = new IssuanceSheetSync(
                new IssuanceSheetProperties(enabled, spreadsheetId, "/etc/key.json", null), queryService, writer, 20);
        return sync;
    }

    @AfterEach
    void tearDown() {
        sync.stop();
    }

    private void tables(List<List<String>> lab, List<List<String>> farm) {
        Map<String, List<List<String>>> tables = new LinkedHashMap<>();
        tables.put("LAB", lab);
        tables.put("FARM", farm);
        when(queryService.rowsByServer()).thenReturn(tables);
    }

    @Test
    @DisplayName("문서 ID와 키 경로가 있어도 켜지 않은 환경은 아무것도 하지 않는다")
    void disabledUnlessExplicitlyEnabled() throws Exception {
        sync(false, "sheet").onRequestRowChanged(new RequestRowChangedEvent());
        sync.onApplicationReady();

        Thread.sleep(100);
        verifyNoInteractions(queryService, writer);
    }

    @Test
    @DisplayName("켰어도 문서 ID가 없으면 아무것도 하지 않는다")
    void disabledWithoutConfig() throws Exception {
        sync(" ").onRequestRowChanged(new RequestRowChangedEvent());
        sync.onApplicationReady();

        Thread.sleep(100);
        verifyNoInteractions(queryService, writer);
    }

    @Test
    @DisplayName("신청 행이 바뀌면 서버 이름에 접미사를 붙인 탭에 쓴다")
    void writesSuffixedTabsOnChange() {
        tables(HEADER_ONLY, ONE_ROW);

        sync("sheet").onRequestRowChanged(new RequestRowChangedEvent());

        verify(writer, timeout(2000)).replaceTab("LAB(자동화)", HEADER_ONLY);
        verify(writer, timeout(2000)).replaceTab("FARM(자동화)", ONE_ROW);
    }

    @Test
    @DisplayName("서버가 뜨면 한 번 맞춘다")
    void syncsOnStartup() {
        tables(HEADER_ONLY, ONE_ROW);

        sync("sheet").onApplicationReady();

        verify(writer, timeout(2000)).replaceTab("FARM(자동화)", ONE_ROW);
    }

    @Test
    @DisplayName("내용이 그대로면 다시 쓰지 않고, 바뀐 탭만 다시 쓴다")
    void skipsUnchangedTabs() {
        sync("sheet");
        tables(HEADER_ONLY, HEADER_ONLY);
        assertThat(sync.syncNow()).isTrue();
        assertThat(sync.syncNow()).isTrue();
        tables(HEADER_ONLY, ONE_ROW);
        assertThat(sync.syncNow()).isTrue();

        verify(writer, times(1)).replaceTab("LAB(자동화)", HEADER_ONLY);
        verify(writer, times(1)).replaceTab("FARM(자동화)", HEADER_ONLY);
        verify(writer, times(1)).replaceTab("FARM(자동화)", ONE_ROW);
    }

    @Test
    @DisplayName("한 탭이 실패해도 다른 탭은 쓰고, 실패한 탭은 변경이 더 없어도 잠시 뒤 다시 쓴다")
    void retriesFailedTabWithoutNewChange() {
        tables(HEADER_ONLY, ONE_ROW);
        doThrow(new IllegalStateException("503")).doNothing().when(writer).replaceTab("LAB(자동화)", HEADER_ONLY);

        sync("sheet").onRequestRowChanged(new RequestRowChangedEvent());

        verify(writer, timeout(2000).times(2)).replaceTab("LAB(자동화)", HEADER_ONLY);
        verify(writer, times(1)).replaceTab("FARM(자동화)", ONE_ROW);
    }

    @Test
    @DisplayName("내역 조회가 실패하면 쓰지 않고 다시 할 대상으로 남긴다")
    void queryFailureIsNotSynced() {
        when(queryService.rowsByServer()).thenThrow(new IllegalStateException("db"));

        assertThat(sync("sheet").syncNow()).isFalse();

        verify(writer, never()).replaceTab(any(), any());
    }
}
