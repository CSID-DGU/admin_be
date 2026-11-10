package DGU_AI_LAB.admin_be.domain.issuanceSheet;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Google Sheets API(v4)로 탭을 다시 쓴다. 값은 입력한 그대로(RAW) 넣는다 — 이름·전화번호가 수식이나 숫자로
 * 해석되지 않는다.
 *
 * <p>탭을 먼저 비우지 않고 새 내용을 덮어쓴 뒤 그 아래와 오른쪽에 남은 칸만 지운다. 중간에 실패해도 탭이 빈 채로
 * 남지 않고, 열이 줄어도 옛 열이 남지 않는다.
 */
@Component
public class GoogleSheetsTabWriter implements SheetTabWriter {

    static final String API = "https://sheets.googleapis.com/v4/spreadsheets";
    private static final String TAB_FIELDS = "sheets.properties(sheetId,title,gridProperties(rowCount,columnCount))";
    private static final int NEW_TAB_ROW_COUNT = 1000;
    private static final int NEW_TAB_COLUMN_COUNT = 26;

    private final String spreadsheetId;
    private final RestClient http;
    private final Supplier<String> accessToken;

    @Autowired
    public GoogleSheetsTabWriter(IssuanceSheetProperties properties) {
        this(properties.spreadsheetId(), properties.credentialsPath(), timeoutClient());
    }

    private GoogleSheetsTabWriter(String spreadsheetId, String credentialsPath, RestClient http) {
        this(spreadsheetId, http,
                new GoogleServiceAccountTokens(credentialsPath, http, Clock.systemUTC())::accessToken);
    }

    GoogleSheetsTabWriter(String spreadsheetId, RestClient http, Supplier<String> accessToken) {
        this.spreadsheetId = spreadsheetId;
        this.http = http;
        this.accessToken = accessToken;
    }

    private static RestClient timeoutClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(30_000);
        return RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public void replaceTab(String tabName, List<List<String>> rows) {
        replaceTab(tabName, rows, null);
    }

    @Override
    public void replaceTab(String tabName, List<List<String>> rows, TabLayout layout) {
        String token = accessToken.get();
        int columnCount = rows.get(0).size();
        Grid grid = ensureTab(token, tabName, columnCount);
        String quotedTab = "'" + tabName.replace("'", "''") + "'";

        http.put().uri(API + "/{id}/values/{range}?valueInputOption=RAW", spreadsheetId, quotedTab + "!A1")
                .headers(headers -> headers.setBearerAuth(token))
                .body(Map.of("values", rows))
                .retrieve().toBodilessEntity();

        // 쓰기 전 크기보다 내용이 크면 탭이 그만큼 늘어난 것이라 그쪽에는 남은 칸이 없다.
        if (rows.size() < grid.rowCount()) {
            clear(token, quotedTab + "!A" + (rows.size() + 1) + ":ZZ");
        }
        if (columnCount < grid.columnCount()) {
            clear(token, quotedTab + "!" + columnLetters(columnCount + 1) + "1:ZZ" + rows.size());
        }
        if (layout != null) {
            applyLayout(token, grid.sheetId(), rows.size(), columnCount, layout);
        }
    }

    private static final Map<String, Object> WHITE = color(255, 255, 255);
    private static final Map<String, Object> INK = color(30, 30, 30);
    private static final Map<String, Object> NOTE = color(89, 89, 89);
    private static final Map<String, Object> HEADER_FILL = color(232, 234, 237);
    private static final Map<String, Object> CALCULATED_FILL = color(247, 247, 247);
    private static final int TITLE_ROW_PX = 45;
    private static final int BODY_ROW_PX = 64;

    private static Map<String, Object> color(int red, int green, int blue) {
        return Map.of("red", red / 255.0, "green", green / 255.0, "blue", blue / 255.0);
    }

    /** 탭의 모양을 맞춘다. 같은 모양을 다시 적용해도 결과가 같으므로, 쓸 때마다 한 번에 보낸다. */
    private void applyLayout(String token, long sheetId, int rowCount, int columnCount, TabLayout layout) {
        int header = layout.headerRow();
        List<Map<String, Object>> requests = new ArrayList<>();
        requests.add(Map.of("updateSheetProperties", Map.of(
                "properties", Map.of("sheetId", sheetId, "gridProperties",
                        Map.of("frozenRowCount", header + 1, "frozenColumnCount", layout.frozenColumns())),
                "fields", "gridProperties.frozenRowCount,gridProperties.frozenColumnCount")));
        requests.add(Map.of("unmergeCells", Map.of("range", range(sheetId, 0, rowCount, 0, columnCount))));
        for (TabLayout.Merge merge : layout.merges()) {
            requests.add(Map.of("mergeCells", Map.of(
                    "range", range(sheetId, merge.row(), merge.row() + 1, merge.fromColumn(), merge.toColumn() + 1),
                    "mergeType", "MERGE_ALL")));
        }
        requests.add(format(range(sheetId, 0, rowCount, 0, columnCount), Map.of(
                "backgroundColor", WHITE, "verticalAlignment", "TOP", "wrapStrategy", "WRAP",
                "textFormat", Map.of("bold", false, "fontSize", 10, "fontFamily", "Arial", "foregroundColor", INK)),
                "userEnteredFormat.backgroundColor,userEnteredFormat.verticalAlignment,userEnteredFormat.wrapStrategy,userEnteredFormat.textFormat"));
        requests.add(format(range(sheetId, 0, 1, 0, columnCount), Map.of(
                "verticalAlignment", "MIDDLE",
                "textFormat", Map.of("bold", true, "fontSize", 14, "foregroundColor", INK)),
                "userEnteredFormat.verticalAlignment,userEnteredFormat.textFormat"));
        if (header > 1) {
            requests.add(format(range(sheetId, 1, header, 0, columnCount), Map.of(
                    "verticalAlignment", "MIDDLE", "textFormat", Map.of("foregroundColor", NOTE)),
                    "userEnteredFormat.verticalAlignment,userEnteredFormat.textFormat.foregroundColor"));
        }
        requests.add(format(range(sheetId, header, header + 1, 0, columnCount), Map.of(
                "backgroundColor", HEADER_FILL, "verticalAlignment", "MIDDLE", "textFormat", Map.of("bold", true)),
                "userEnteredFormat.backgroundColor,userEnteredFormat.verticalAlignment,userEnteredFormat.textFormat.bold"));
        if (rowCount > header + 1) {
            requests.add(format(range(sheetId, header + 1, rowCount, layout.calculatedFrom(), layout.calculatedTo() + 1),
                    Map.of("backgroundColor", CALCULATED_FILL), "userEnteredFormat.backgroundColor"));
            requests.add(size(sheetId, "ROWS", header + 1, rowCount, BODY_ROW_PX));
        }
        requests.add(size(sheetId, "ROWS", 0, header + 1, TITLE_ROW_PX));
        for (int column = 0; column < layout.columnWidthsPx().size(); column++) {
            requests.add(size(sheetId, "COLUMNS", column, column + 1, layout.columnWidthsPx().get(column)));
        }
        requests.add(Map.of("setBasicFilter", Map.of("filter",
                Map.of("range", range(sheetId, header, rowCount, 0, columnCount)))));
        batchUpdate(token, requests);
    }

    private static Map<String, Object> range(long sheetId, int startRow, int endRow, int startColumn, int endColumn) {
        return Map.of("sheetId", sheetId, "startRowIndex", startRow, "endRowIndex", endRow,
                "startColumnIndex", startColumn, "endColumnIndex", endColumn);
    }

    private static Map<String, Object> format(Map<String, Object> range, Map<String, Object> cellFormat, String fields) {
        return Map.of("repeatCell", Map.of("range", range,
                "cell", Map.of("userEnteredFormat", cellFormat), "fields", fields));
    }

    private static Map<String, Object> size(long sheetId, String dimension, int start, int end, int pixels) {
        return Map.of("updateDimensionProperties", Map.of(
                "range", Map.of("sheetId", sheetId, "dimension", dimension, "startIndex", start, "endIndex", end),
                "properties", Map.of("pixelSize", pixels), "fields", "pixelSize"));
    }

    private void clear(String token, String range) {
        http.post().uri(API + "/{id}/values/{range}:clear", spreadsheetId, range)
                .headers(headers -> headers.setBearerAuth(token))
                .retrieve().toBodilessEntity();
    }

    /** 1부터 세는 열 번호를 A, B, …, Z, AA, … 표기로 바꾼다. */
    static String columnLetters(int column) {
        StringBuilder letters = new StringBuilder();
        for (int rest = column; rest > 0; rest = (rest - 1) / 26) {
            letters.insert(0, (char) ('A' + (rest - 1) % 26));
        }
        return letters.toString();
    }

    private record Grid(long sheetId, int rowCount, int columnCount) {

        static Grid of(JsonNode properties) {
            JsonNode gridProperties = properties.path("gridProperties");
            return new Grid(properties.path("sheetId").asLong(),
                    gridProperties.path("rowCount").asInt(NEW_TAB_ROW_COUNT),
                    gridProperties.path("columnCount").asInt(NEW_TAB_COLUMN_COUNT));
        }
    }

    /** @return 탭의 현재 크기 */
    private Grid ensureTab(String token, String tabName, int columnCount) {
        JsonNode spreadsheet = http.get().uri(API + "/{id}?fields={fields}", spreadsheetId, TAB_FIELDS)
                .headers(headers -> headers.setBearerAuth(token))
                .retrieve().body(JsonNode.class);
        if (spreadsheet != null) {
            for (JsonNode sheet : spreadsheet.path("sheets")) {
                JsonNode properties = sheet.path("properties");
                if (tabName.equals(properties.path("title").asText())) {
                    return Grid.of(properties);
                }
            }
        }
        return createTab(token, tabName, columnCount);
    }

    private Grid createTab(String token, String tabName, int columnCount) {
        JsonNode created = batchUpdate(token, Map.of("addSheet", Map.of("properties", Map.of(
                "title", tabName,
                "gridProperties", Map.of("frozenRowCount", 1)))));
        JsonNode properties = created == null
                ? null : created.path("replies").path(0).path("addSheet").path("properties");
        if (properties == null || !properties.hasNonNull("sheetId")) {
            throw new IllegalStateException("탭을 만들었지만 응답에 sheetId가 없다: " + tabName);
        }
        batchUpdate(token, Map.of("repeatCell", Map.of(
                "range", Map.of(
                        "sheetId", properties.get("sheetId").asLong(),
                        "startRowIndex", 0, "endRowIndex", 1,
                        "startColumnIndex", 0, "endColumnIndex", columnCount),
                "cell", Map.of("userEnteredFormat", Map.of("textFormat", Map.of("bold", true))),
                "fields", "userEnteredFormat.textFormat.bold")));
        return Grid.of(properties);
    }

    private JsonNode batchUpdate(String token, Map<String, Object> request) {
        return batchUpdate(token, List.of(request));
    }

    private JsonNode batchUpdate(String token, List<Map<String, Object>> requests) {
        return http.post().uri(API + "/{id}:batchUpdate", spreadsheetId)
                .headers(headers -> headers.setBearerAuth(token))
                .body(Map.of("requests", requests))
                .retrieve().body(JsonNode.class);
    }
}
