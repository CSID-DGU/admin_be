package DGU_AI_LAB.admin_be.global.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.Properties;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class MessagesPropertiesTest {

    @Test
    @DisplayName("모든 안내 문구가 MessageFormat으로 채워지고, 따옴표 실수로 자리표시자가 그대로 남지 않는다")
    void everyTemplateFormats() throws Exception {
        Properties messages = new Properties();
        try (var reader = new InputStreamReader(
                getClass().getClassLoader().getResourceAsStream("messages.properties"), StandardCharsets.UTF_8)) {
            messages.load(reader);
        }
        Object[] args = IntStream.range(0, 21).mapToObj(i -> "ARG" + i).toArray();

        assertThat(messages).isNotEmpty();
        for (String key : messages.stringPropertyNames()) {
            String template = messages.getProperty(key);
            assertThatCode(() -> new MessageFormat(template, Locale.KOREA).format(args)).as(key).doesNotThrowAnyException();
            assertThat(new MessageFormat(template, Locale.KOREA).format(args)).as(key).doesNotContain("{").doesNotContain("}");
        }
    }

    @Test
    @DisplayName("비활성화 안내문은 홈 디렉터리와 이력이 어떻게 되는지 알려 준다")
    void inactivationNoticesExplainHomeAndHistory() throws Exception {
        Properties messages = new Properties();
        try (var reader = new InputStreamReader(
                getClass().getClassLoader().getResourceAsStream("messages.properties"), StandardCharsets.UTF_8)) {
            messages.load(reader);
        }

        for (String key : new String[]{"notification.user.delete-warning.body", "notification.user.soft-delete.body"}) {
            assertThat(messages.getProperty(key)).as(key).contains("홈 디렉터리").contains("이력");
        }
    }
}
