package DGU_AI_LAB.admin_be.domain.issuanceSheet;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Clock;
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

    private record Grid(int rowCount, int columnCount) {

        static Grid of(JsonNode gridProperties) {
            return new Grid(gridProperties.path("rowCount").asInt(NEW_TAB_ROW_COUNT),
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
                    return Grid.of(properties.path("gridProperties"));
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
        return Grid.of(properties.path("gridProperties"));
    }

    private JsonNode batchUpdate(String token, Map<String, Object> request) {
        return http.post().uri(API + "/{id}:batchUpdate", spreadsheetId)
                .headers(headers -> headers.setBearerAuth(token))
                .body(Map.of("requests", List.of(request)))
                .retrieve().body(JsonNode.class);
    }
}
