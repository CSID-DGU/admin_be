package DGU_AI_LAB.admin_be.domain.alarm;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 알림 글(mrkdwn)을 Slack 블록 양식으로 바꾼다. 양식(messages.properties, 관리자가 고친 DB 양식)은 글 그대로 두고,
 * 글의 모양만 보고 나눈다.
 * <ul>
 *   <li>빈 줄로 나뉜 덩어리 하나가 구역 하나이고, {@code *소제목*}으로 시작하는 구역 앞에 구분선을 넣는다.</li>
 *   <li>첫 덩어리가 {@code *제목*} 한 줄이면 큰 제목으로 올린다.</li>
 *   <li>{@code *소제목*} 줄 아래가 전부 {@code • 이름: 값} 줄이면 이름·값을 2단 표로 놓는다.</li>
 *   <li>그 밖의 덩어리는 글 그대로 넣는다.</li>
 * </ul>
 * Slack 한도를 넘는 글은 빈 목록을 돌려준다 — 호출부는 블록 없이 글만 보낸다.
 *
 * <p>줄 모양이 곧 구조이므로, 양식에 넣는 사용자 입력은 {@link SlackText#line}(한 줄 값)이나
 * {@link SlackText#quote}(여러 줄 글)로 넣어야 한다. 그대로 넣으면 입력에 든 빈 줄·소제목·항목 줄이 구역으로 읽힌다.
 */
public final class SlackBlocks {

    static final int MAX_BLOCKS = 50;
    static final int MAX_HEADER_LENGTH = 150;
    static final int MAX_SECTION_LENGTH = 3000;
    static final int MAX_FIELD_LENGTH = 2000;
    static final int MAX_FIELDS_PER_SECTION = 10;

    private static final String BULLET = "• ";
    private static final String LABEL_SEPARATOR = ": ";

    private SlackBlocks() {}

    public static List<Map<String, Object>> fromMrkdwn(String message) {
        if (message == null || message.isBlank()) {
            return List.of();
        }
        List<Map<String, Object>> blocks = new ArrayList<>();
        String[] chunks = message.strip().split("\\n[ \\t]*\\n\\s*");
        for (int i = 0; i < chunks.length; i++) {
            List<String> lines = chunks[i].lines().map(String::strip).filter(line -> !line.isEmpty()).toList();
            if (lines.isEmpty()) {
                continue;
            }
            if (i == 0 && lines.size() == 1 && isBold(lines.get(0)) && lines.get(0).length() - 2 <= MAX_HEADER_LENGTH) {
                blocks.add(header(unwrap(lines.get(0))));
                continue;
            }
            // 구분선은 소제목으로 시작하는 구역 앞에만 넣는다 — 사용자가 적은 글의 문단 사이까지 끊지 않는다.
            if (isBold(lines.get(0)) && !blocks.isEmpty() && !"header".equals(blocks.get(blocks.size() - 1).get("type"))) {
                blocks.add(Map.of("type", "divider"));
            }
            if (isLabelledList(lines)) {
                addFieldSections(blocks, lines);
            } else {
                blocks.add(Map.of("type", "section", "text", mrkdwn(String.join("\n", lines))));
            }
        }
        return withinLimits(blocks) ? List.copyOf(blocks) : List.of();
    }

    private static boolean isBold(String line) {
        return line.length() > 2 && line.startsWith("*") && line.endsWith("*")
                && line.indexOf('*', 1) == line.length() - 1;
    }

    private static String unwrap(String boldLine) {
        return boldLine.substring(1, boldLine.length() - 1);
    }

    private static boolean isLabelledList(List<String> lines) {
        return lines.size() > 1 && isBold(lines.get(0))
                && lines.stream().skip(1).allMatch(line -> line.startsWith(BULLET) && line.indexOf(LABEL_SEPARATOR) > BULLET.length());
    }

    private static void addFieldSections(List<Map<String, Object>> blocks, List<String> lines) {
        List<Map<String, Object>> fields = lines.stream().skip(1).map(SlackBlocks::field).toList();
        for (int from = 0; from < fields.size(); from += MAX_FIELDS_PER_SECTION) {
            List<Map<String, Object>> part = fields.subList(from, Math.min(from + MAX_FIELDS_PER_SECTION, fields.size()));
            blocks.add(from == 0
                    ? Map.of("type", "section", "text", mrkdwn(lines.get(0)), "fields", part)
                    : Map.of("type", "section", "fields", part));
        }
    }

    private static Map<String, Object> field(String bulletLine) {
        int separator = bulletLine.indexOf(LABEL_SEPARATOR);
        String label = bulletLine.substring(BULLET.length(), separator).replace("*", "");
        String value = bulletLine.substring(separator + LABEL_SEPARATOR.length());
        return mrkdwn("*" + label + "*\n" + value);
    }

    /** 이스케이프된 문자(&amp;lt; 등)는 되돌리지 않는다 — 되돌리면 값에 든 {@code <!channel>} 같은 글이 되살아난다. */
    private static Map<String, Object> header(String title) {
        return Map.of("type", "header", "text", Map.of("type", "plain_text", "text", title, "emoji", true));
    }

    private static Map<String, Object> mrkdwn(String text) {
        return Map.of("type", "mrkdwn", "text", text);
    }

    @SuppressWarnings("unchecked")
    private static boolean withinLimits(List<Map<String, Object>> blocks) {
        if (blocks.isEmpty() || blocks.size() > MAX_BLOCKS) {
            return false;
        }
        for (Map<String, Object> block : blocks) {
            Map<String, Object> text = (Map<String, Object>) block.get("text");
            if ("section".equals(block.get("type")) && text != null && length(text) > MAX_SECTION_LENGTH) {
                return false;
            }
            List<Map<String, Object>> fields = (List<Map<String, Object>>) block.get("fields");
            if (fields != null && fields.stream().anyMatch(field -> length(field) > MAX_FIELD_LENGTH)) {
                return false;
            }
        }
        return true;
    }

    private static int length(Map<String, Object> text) {
        return ((String) text.get("text")).length();
    }
}
