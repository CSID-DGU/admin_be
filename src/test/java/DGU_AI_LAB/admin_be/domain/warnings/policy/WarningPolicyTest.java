package DGU_AI_LAB.admin_be.domain.warnings.policy;

import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.warnings.entity.UserWarning;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WarningPolicyTest {

    private final User user = User.builder().email("alice@dgu.ac.kr").name("alice").build();
    private final User admin = User.builder().email("admin@dgu.ac.kr").name("admin").build();
    private final List<UserWarning> rows = new ArrayList<>();

    private UserWarning grant() {
        return add(UserWarning.grant(user, admin, "사유"));
    }

    private void deduct() {
        add(UserWarning.deduct(user, admin, "사유"));
    }

    private void cancel(UserWarning granted) {
        add(UserWarning.cancel(granted, admin, "사유"));
    }

    private UserWarning add(UserWarning row) {
        ReflectionTestUtils.setField(row, "warningId", (long) rows.size() + 1);
        rows.add(row);
        return row;
    }

    private WarningLedger ledger() {
        return WarningLedger.replay(rows);
    }

    @ParameterizedTest(name = "경고 {0}회 → 정지 {1}일")
    @CsvSource({"0,0", "1,0", "2,7", "3,14", "4,21", "10,63"})
    @DisplayName("정지 기간은 7 × (횟수 − 1)일이고 1회는 경고만이다")
    void suspensionDaysFollowTheAnnouncedRule(int count, int days) {
        assertThat(WarningPolicy.suspensionDays(count)).isEqualTo(days);
    }

    @Test
    @DisplayName("경고가 없으면 0회이고 다음 경고에는 정지가 따르지 않는다")
    void emptyLedger() {
        assertThat(ledger()).isEqualTo(new WarningLedger(0, 0));
        assertThat(ledger().nextSuspensionDays()).isZero();
        assertThat(ledger().deductible()).isFalse();
    }

    @Test
    @DisplayName("2회까지는 차감할 수 없고 다음 경고의 정지 기간이 늘어난다")
    void firstTwoWarningsAccumulateWithoutDeduction() {
        grant();
        assertThat(ledger().nextSuspensionDays()).isEqualTo(7);
        grant();

        assertThat(ledger().count()).isEqualTo(2);
        assertThat(ledger().deductible()).isFalse();
        assertThat(ledger().nextSuspensionDays()).isEqualTo(14);
    }

    @Test
    @DisplayName("3회가 쌓인 적이 있으면 0회까지 하나씩 차감할 수 있다")
    void deductionUnlocksAtThreeAndGoesDownToZero() {
        grant();
        grant();
        grant();
        assertThat(ledger().deductible()).isTrue();

        deduct();
        deduct();
        assertThat(ledger().count()).isEqualTo(1);
        assertThat(ledger().deductible()).isTrue();

        deduct();
        assertThat(ledger()).isEqualTo(new WarningLedger(0, 3));
        assertThat(ledger().deductible()).isFalse();
    }

    @Test
    @DisplayName("차감한 뒤 다시 받은 경고는 줄어든 횟수에서 이어 센다")
    void warningAfterDeductionContinuesFromTheReducedCount() {
        grant();
        grant();
        grant();
        deduct();

        assertThat(ledger().count()).isEqualTo(2);
        assertThat(ledger().nextSuspensionDays()).isEqualTo(14);
    }

    @Test
    @DisplayName("취소된 경고는 처음부터 없던 것으로 센다 — 3회 이력도 생기지 않는다")
    void canceledWarningNeverCounted() {
        grant();
        grant();
        UserWarning mistaken = grant();
        cancel(mistaken);

        assertThat(ledger()).isEqualTo(new WarningLedger(2, 2));
        assertThat(ledger().deductible()).isFalse();
        assertThat(WarningLedger.canceledWarningIds(rows)).containsExactly(mistaken.getWarningId());
    }
}
