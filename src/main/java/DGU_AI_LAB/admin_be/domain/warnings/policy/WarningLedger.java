package DGU_AI_LAB.admin_be.domain.warnings.policy;

import DGU_AI_LAB.admin_be.domain.warnings.entity.UserWarning;
import DGU_AI_LAB.admin_be.domain.warnings.entity.WarningType;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 경고 대장을 처음부터 다시 읽어 구한 현재 상태. 횟수를 따로 저장하지 않으므로 대장과 어긋날 수 없다.
 *
 * <p>취소된 경고는 처음부터 없던 것으로 센다 — 잘못 준 경고 때문에 "3회 이상 쌓인 적이 있다"가 되지 않게 한다.
 *
 * @param count 현재 경고 횟수
 * @param peak  지금까지 가장 많이 쌓였던 횟수
 */
public record WarningLedger(int count, int peak) {

    /** @param rows 한 사용자의 대장 줄 전부, 오래된 순 */
    public static WarningLedger replay(List<UserWarning> rows) {
        Set<Long> canceledIds = canceledWarningIds(rows);
        int count = 0;
        int peak = 0;
        for (UserWarning row : rows) {
            if (row.getType() == WarningType.GRANT && !canceledIds.contains(row.getWarningId())) {
                count++;
                peak = Math.max(peak, count);
            } else if (row.getType() == WarningType.DEDUCT) {
                count = Math.max(0, count - 1);
            }
        }
        return new WarningLedger(count, peak);
    }

    /** 취소된 부여 줄의 번호. */
    public static Set<Long> canceledWarningIds(List<UserWarning> rows) {
        return rows.stream()
                .filter(row -> row.getType() == WarningType.CANCEL)
                .map(row -> row.getCanceledWarning().getWarningId())
                .collect(Collectors.toSet());
    }

    /** 지금 차감할 수 있는가. */
    public boolean deductible() {
        return peak >= WarningPolicy.DEDUCTION_UNLOCK_COUNT && count > 0;
    }

    /** 경고를 하나 더 주면 따르는 이용 정지 일수. */
    public int nextSuspensionDays() {
        return WarningPolicy.suspensionDays(count + 1);
    }
}
