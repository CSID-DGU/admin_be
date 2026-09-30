package DGU_AI_LAB.admin_be.domain.alarm;

import java.util.stream.Collectors;

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
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** 여러 줄 글을 인용 블록으로 감싼다. 줄마다 "> "를 붙여야 Slack이 전부 인용으로 보여 준다. */
    public static String quote(String text) {
        return escape(text).strip().lines()
                .map(line -> "> " + line)
                .collect(Collectors.joining("\n"));
    }
}
