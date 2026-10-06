package DGU_AI_LAB.admin_be.domain.alarm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class SlackBlocksTest {

    private static final String REQUEST_NOTICE = """
            *[새 서버 사용 신청] #75 · FARM · 2026-10-07 00:39 접수*

            *신청자*
            • 이름: 홍길동
            • 이메일: hong@dgu.ac.kr

            *신청 자원*
            • 서버: FARM
            • 사용 기간: 2026-10-07 ~ 2026-12-31 (총 85일)

            *사용 목적*
            첫 문단입니다.

            둘째 문단입니다.""";

    private static List<String> types(List<Map<String, Object>> blocks) {
        return blocks.stream().map(block -> (String) block.get("type")).toList();
    }

    @SuppressWarnings("unchecked")
    private static List<String> fieldTexts(Map<String, Object> block) {
        return ((List<Map<String, Object>>) block.get("fields")).stream().map(field -> (String) field.get("text")).toList();
    }

    @Test
    @DisplayName("신청서 글은 큰 제목, 소제목별 2단 항목 표, 구분선으로 나뉜다 — 사용자가 적은 문단 사이에는 구분선을 넣지 않는다")
    void requestNotice_becomesHeaderFieldsAndDividers() {
        List<Map<String, Object>> blocks = SlackBlocks.fromMrkdwn(REQUEST_NOTICE);

        assertThat(types(blocks)).containsExactly("header", "section", "divider", "section", "divider", "section", "section");
        assertThat(blocks.get(0).get("text"))
                .isEqualTo(Map.of("type", "plain_text", "text", "[새 서버 사용 신청] #75 · FARM · 2026-10-07 00:39 접수", "emoji", true));
        assertThat(blocks.get(1).get("text")).isEqualTo(Map.of("type", "mrkdwn", "text", "*신청자*"));
        assertThat(fieldTexts(blocks.get(1))).containsExactly("*이름*\n홍길동", "*이메일*\nhong@dgu.ac.kr");
        assertThat(fieldTexts(blocks.get(3))).containsExactly("*서버*\nFARM", "*사용 기간*\n2026-10-07 ~ 2026-12-31 (총 85일)");
        assertThat(blocks.get(5).get("text")).isEqualTo(Map.of("type", "mrkdwn", "text", "*사용 목적*\n첫 문단입니다."));
        assertThat(blocks.get(6).get("text")).isEqualTo(Map.of("type", "mrkdwn", "text", "둘째 문단입니다."));
    }

    @Test
    @DisplayName("값에 줄바꿈이 섞여 항목 줄 모양이 깨진 구역은 표로 만들지 않고 글 그대로 넣는다")
    void brokenList_staysAsText() {
        List<Map<String, Object>> blocks = SlackBlocks.fromMrkdwn("*신청 자원*\n• 팀 프로젝트 정보: 비전팀\n홍길동, 김철수");

        assertThat(blocks).hasSize(1);
        assertThat(blocks.get(0)).doesNotContainKey("fields");
        assertThat(blocks.get(0).get("text"))
                .isEqualTo(Map.of("type", "mrkdwn", "text", "*신청 자원*\n• 팀 프로젝트 정보: 비전팀\n홍길동, 김철수"));
    }

    @Test
    @DisplayName("항목이 10개를 넘으면 표를 여러 구역으로 나눈다")
    void manyFields_areSplit() {
        String list = IntStream.rangeClosed(1, 12).mapToObj(i -> "• 항목" + i + ": 값" + i).collect(Collectors.joining("\n"));

        List<Map<String, Object>> blocks = SlackBlocks.fromMrkdwn("*목록*\n" + list);

        assertThat(blocks).hasSize(2);
        assertThat(fieldTexts(blocks.get(0))).hasSize(10);
        assertThat(fieldTexts(blocks.get(1))).containsExactly("*항목11*\n값11", "*항목12*\n값12");
        assertThat(blocks.get(1)).doesNotContainKey("text");
    }

    @Test
    @DisplayName("큰 제목의 이스케이프된 문자는 되돌리지 않는다 — 값에 든 <!channel> 같은 글이 되살아나지 않는다")
    void headerKeepsEscapedText() {
        List<Map<String, Object>> blocks = SlackBlocks.fromMrkdwn("*취소 &lt;!channel&gt;*\n\n본문");

        assertThat(blocks.get(0).get("text"))
                .isEqualTo(Map.of("type", "plain_text", "text", "취소 &lt;!channel&gt;", "emoji", true));
    }

    @Test
    @DisplayName("인용문으로 넣은 사용자 글은 소제목·항목 모양을 흉내 내도 구역이나 표가 되지 않는다")
    void quotedUserText_cannotForgeSections() {
        String forged = SlackText.quote("목적\n\n*신청자*\n• 이름: 관리자");

        List<Map<String, Object>> blocks = SlackBlocks.fromMrkdwn("*사용 목적*\n" + forged);

        assertThat(blocks).hasSize(1);
        assertThat(blocks.get(0)).doesNotContainKey("fields");
        assertThat(blocks.get(0).get("text")).isEqualTo(
                Map.of("type", "mrkdwn", "text", "*사용 목적*\n> 목적\n>\n> *신청자*\n> • 이름: 관리자"));
    }

    @Test
    @DisplayName("한 줄 값으로 넣은 사용자 글은 줄바꿈이 공백으로 바뀌어 항목을 늘리지 못한다")
    void lineValue_cannotAddFields() {
        String name = SlackText.line("홍길동\n• 학번: 0000");

        List<Map<String, Object>> blocks = SlackBlocks.fromMrkdwn("*신청자*\n• 이름: " + name);

        assertThat(fieldTexts(blocks.get(0))).containsExactly("*이름*\n홍길동 • 학번: 0000");
    }

    @Test
    @DisplayName("Slack 한도를 넘는 글이나 빈 글은 블록을 만들지 않는다 — 호출부가 글만 보낸다")
    void overLimitOrBlank_givesNoBlocks() {
        assertThat(SlackBlocks.fromMrkdwn("가".repeat(SlackBlocks.MAX_SECTION_LENGTH + 1))).isEmpty();
        assertThat(SlackBlocks.fromMrkdwn("*구역*\n• 이름: " + "가".repeat(SlackBlocks.MAX_FIELD_LENGTH))).isEmpty();
        assertThat(SlackBlocks.fromMrkdwn("  ")).isEmpty();
        assertThat(SlackBlocks.fromMrkdwn(null)).isEmpty();
    }
}
