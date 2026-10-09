package DGU_AI_LAB.admin_be.domain.warnings.policy;

/**
 * 경고 제도의 숫자. 공지된 기준 그대로다.
 *
 * <pre>
 * 경고 1회: 경고만
 * 경고 n회(n ≥ 2): 이용 정지 7 × (n − 1)일
 * 차감: 경고가 3회 이상 쌓인 적이 있어야 할 수 있고, 0회까지 내려간다
 * </pre>
 */
public final class WarningPolicy {

    /** 이 횟수부터 이용 정지가 따른다. */
    public static final int SUSPENSION_FROM_COUNT = 2;
    /** 정지 기간의 단위(일). */
    public static final int SUSPENSION_UNIT_DAYS = 7;
    /** 경고가 이만큼 쌓인 적이 있어야 차감할 수 있다. */
    public static final int DEDUCTION_UNLOCK_COUNT = 3;

    private WarningPolicy() {
    }

    /** 경고가 {@code count}회가 됐을 때의 이용 정지 일수. 정지가 없으면 0. */
    public static int suspensionDays(int count) {
        return count < SUSPENSION_FROM_COUNT ? 0 : SUSPENSION_UNIT_DAYS * (count - 1);
    }
}
