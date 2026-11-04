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

    @Test
    @DisplayName("경고 표는 종류·취소 여부·정지 기간을 싣고 관리자도 대상이 된다")
    void warningsCarryTypeCancelAndSuspension() {
        User admin = user(2, "root");
        UserWarning granted = warning(1, UserWarning.grant(admin, admin, "사유"));
        UserWarning cancel = warning(2, UserWarning.cancel(granted, admin, "취소"));
        UserSuspension suspension = new UserSuspension(granted, LocalDateTime.of(2026, 11, 4, 9, 0), 7);

        List<List<String>> rows = AccountSheetRows.warnings(List.of(cancel, granted), List.of(suspension));

        assertThat(rows.get(1)).containsExactly("1", "2", "root", "root", "부여", "사유", "root", "",
                "Y", "", "2026-11-04 09:00", "2026-11-11 09:00");
        assertThat(rows.get(2).get(4)).isEqualTo("취소");
        assertThat(rows.get(2).get(9)).isEqualTo("1");
    }

    @Test
    @DisplayName("대장이 비어도 머리글만 있는 표가 나온다")
    void emptyLedgerGivesHeaderOnly() {
        assertThat(AccountSheetRows.warnings(List.of(), List.of())).containsExactly(AccountSheetRows.WARNING_HEADER);
        assertThat(AccountSheetRows.users(List.of(), List.of())).containsExactly(AccountSheetRows.USER_HEADER);
    }
}
