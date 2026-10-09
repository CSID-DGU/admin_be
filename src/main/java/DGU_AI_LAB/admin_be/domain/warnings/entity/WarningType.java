package DGU_AI_LAB.admin_be.domain.warnings.entity;

/** 경고 대장에 남는 일의 종류. */
public enum WarningType {
    /** 경고 부여. 횟수가 하나 는다. */
    GRANT,
    /** 절차를 지킨 신고에 따른 차감. 횟수가 하나 준다. */
    DEDUCT,
    /** 잘못 준 경고의 취소. 그 경고는 처음부터 없던 것으로 센다. */
    CANCEL
}
