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

    /** {@link #escape}와 같되 빈 값을 "-"로 바꾸지 않는다. 알림 문구에 넣는 값을 한꺼번에 이스케이프할 때 쓴다. */
    public static String escapeKeepingEmpty(String text) {
        if (text == null) {
            return null;
        }
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
