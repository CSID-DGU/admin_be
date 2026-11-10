package DGU_AI_LAB.admin_be.domain.issuanceSheet;

import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.warnings.entity.UserSuspension;
import DGU_AI_LAB.admin_be.domain.warnings.entity.UserWarning;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AccountSheetRows")
class AccountSheetRowsTest {

    private static User user(long id, String name) {
        User user = User.builder().email(name + "@dgu.ac.kr").password("secret-hash").name(name)
                .studentId("2025").phone("010").department("CS").ubuntuUsername(name).build();
        ReflectionTestUtils.setField(user, "userId", id);
        return user;
    }

    private static UserWarning warning(long id, UserWarning row) {
        ReflectionTestUtils.setField(row, "warningId", id);
        return row;
    }

    @Test
    @DisplayName("사용자 표는 현재 경고 횟수를 싣고 비밀번호는 싣지 않는다")
    void usersCarryWarningCountWithoutPassword() {
        User alice = user(1, "alice");
        User admin = user(2, "root");
        UserWarning granted = warning(1, UserWarning.grant(alice, admin, "r"));
        UserWarning canceled = warning(2, UserWarning.grant(alice, admin, "r2"));
        UserWarning cancel = warning(3, UserWarning.cancel(canceled, admin, "oops"));

        List<List<String>> rows = AccountSheetRows.users(List.of(admin, alice), List.of(granted, canceled, cancel));

        assertThat(rows.get(0)).isEqualTo(AccountSheetRows.USER_HEADER);
        assertThat(rows.get(1).get(1)).isEqualTo("alice");
        assertThat(rows.get(1).get(13)).isEqualTo("1");
        assertThat(rows.get(2).get(13)).isEqualTo("0");
        assertThat(rows.toString()).doesNotContain("secret-hash");
    }

    private static final AccountSheetRows.Messages MESSAGES =
            (code, args) -> code + java.util.Arrays.toString(args);

    @Test
    @DisplayName("경고 표는 앞 두 행이 제목·안내이고 세 번째 행이 경고 카운트 탭과 같은 머리글이다")
    void warningsStartWithTitleNoteAndHeader() {
        List<List<String>> rows = AccountSheetRows.warnings(List.of(), List.of(), MESSAGES);

        assertThat(rows).hasSize(3);
        assertThat(rows.get(0).get(0)).isEqualTo("경고 관리 · Provi 자동 기록");
        assertThat(rows.get(1).get(0)).isEqualTo("안내");
        assertThat(rows.get(2)).isEqualTo(AccountSheetRows.WARNING_HEADER);
        assertThat(rows).allSatisfy(row -> assertThat(row).hasSameSizeAs(AccountSheetRows.WARNING_HEADER));
        assertThat(AccountSheetRows.WARNING_LAYOUT.columnWidthsPx()).hasSameSizeAs(AccountSheetRows.WARNING_HEADER);
    }

    @Test
    @DisplayName("부여·정지·차감을 그 시점의 횟수와 증감, 안내 문구로 싣는다")
    void warningsReplayCountsPerRow() {
        User alice = user(1, "alice");
        User admin = user(2, "root");
        UserWarning g1 = warning(1, UserWarning.grant(alice, admin, "r1"));
        UserWarning g2 = warning(2, UserWarning.grant(alice, admin, "r2"));
        UserWarning g3 = warning(3, UserWarning.grant(alice, admin, "r3"));
        UserWarning d4 = warning(4, UserWarning.deduct(alice, admin, "ok"));
        UserSuspension s2 = new UserSuspension(g2, LocalDateTime.of(2026, 10, 8, 10, 30), 7);
        UserSuspension s3 = new UserSuspension(g3, LocalDateTime.of(2026, 10, 20, 14, 0), 14);

        List<List<String>> rows = AccountSheetRows.warnings(List.of(d4, g3, g1, g2), List.of(s3, s2), MESSAGES);

        assertThat(rows.get(3)).containsExactly("alice", "", "경고 부과", "r1", "root", "0", "+1", "1", "불가", "", "", "",
                "notification.user.warning.granted.body[alice, r1, 1]", "1", "1", "alice", "", "");
        assertThat(rows.get(4)).containsExactly("alice", "", "경고 부과", "r2", "root", "1", "+1", "2", "불가", "7",
                "2026-10-08 10:30", "2026-10-15 10:30",
                "notification.user.warning.suspended.body[alice, r2, 2, 7, 2026-10-15 10:30]", "2", "1", "alice", "", "");
        assertThat(rows.get(5).subList(5, 10)).containsExactly("2", "+1", "3", "가능", "14");
        assertThat(rows.get(6).subList(2, 10)).containsExactly("정상 신고", "ok", "root", "3", "-1", "2", "가능", "");
        assertThat(rows.get(6).get(12)).isEqualTo("notification.user.warning.reduced.body[alice, ok, 2]");
    }

    @Test
    @DisplayName("취소하면 그 시점부터 횟수가 줄고, 해제 예정은 취소로 끝난 실제 시각이며 안내는 처음 알린 시각이다")
    void cancelReducesCountAndKeepsActualEnd() {
        User alice = user(1, "alice");
        User admin = user(2, "root");
        UserWarning g1 = warning(1, UserWarning.grant(alice, admin, "r1"));
        UserWarning g2 = warning(2, UserWarning.grant(alice, admin, "r2"));
        UserWarning cancel = warning(3, UserWarning.cancel(g2, admin, "오부여"));
        UserSuspension s2 = new UserSuspension(g2, LocalDateTime.of(2026, 11, 1, 10, 0), 7);
        s2.endNow(LocalDateTime.of(2026, 11, 3, 9, 0));

        List<List<String>> rows = AccountSheetRows.warnings(List.of(g1, g2, cancel), List.of(s2), MESSAGES);

        assertThat(rows.get(4).get(11)).isEqualTo("2026-11-03 09:00");
        assertThat(rows.get(4).get(12)).contains("2026-11-08 10:00");
        assertThat(rows.get(4).get(16)).isEqualTo("Y");
        assertThat(rows.get(5).subList(2, 10)).containsExactly("취소", "오부여", "root", "2", "-1", "1", "불가", "");
        assertThat(rows.get(5).get(17)).isEqualTo("2");
    }

    @Test
    @DisplayName("사용자 대장이 비어도 머리글만 있는 표가 나온다")
    void emptyLedgerGivesHeaderOnly() {
        assertThat(AccountSheetRows.users(List.of(), List.of())).containsExactly(AccountSheetRows.USER_HEADER);
    }
}
