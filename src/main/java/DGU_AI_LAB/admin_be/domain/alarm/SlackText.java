package DGU_AI_LAB.admin_be.domain.alarm;

/**
 * Slack mrkdwn에 사용자가 입력한 글을 넣을 때 쓰는 도우미.
 * &lt;, &gt;, &amp;를 그대로 두면 사용자가 적은 {@code <!channel>} 같은 글이 채널 전체 호출이나 링크로 바뀐다.
 */
public final class SlackText {

    private SlackText() {}

    public static String escape(String text) {
        if (text == null || text.isBlank()) {
            return "-";
        }
        return escapeKeepingEmpty(text);
    }

    /**
     * 한 줄로 들어갈 값. 줄바꿈을 공백으로 바꾼다 — 블록 양식({@link SlackBlocks})은 줄 모양으로 구역과 항목을 나누므로,
     * 값에 줄바꿈을 섞어 가짜 구역·항목을 만들지 못하게 한다.
     */
    public static String line(String text) {
        return escape(text).strip().replaceAll("\\s*\\R\\s*", " ");
    }

    /**
     * 사용자가 적은 여러 줄 글. 줄마다 인용 표시(&gt;)를 붙인다 — 어디까지가 사용자가 적은 글인지 보이고,
     * 줄이 소제목이나 항목 모양으로 시작하지 못해 블록 양식의 구역으로 읽히지 않는다.
     */
    public static String quote(String text) {
        // 줄바꿈으로 보이는 문자(U+2028, U+0085, 세로 탭 등)를 전부 \n으로 맞춘 뒤 나눈다 — 그대로 두면 인용 표시 없는 줄이 생긴다.
        return escape(text).replaceAll("\\R", "\n").strip().lines()
                .map(line -> line.isBlank() ? ">" : "> " + line.strip())
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    /** {@link #escape}와 같되 빈 값을 "-"로 바꾸지 않는다. 알림 문구에 넣는 값을 한꺼번에 이스케이프할 때 쓴다. */
    public static String escapeKeepingEmpty(String text) {
        if (text == null) {
            return null;
        }
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
