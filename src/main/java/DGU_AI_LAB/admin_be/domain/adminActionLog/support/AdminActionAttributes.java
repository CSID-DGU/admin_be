package DGU_AI_LAB.admin_be.domain.adminActionLog.support;

/** 요청 하나 동안 기록 재료를 모아 두는 요청 속성 이름. */
public final class AdminActionAttributes {

    private static final String PREFIX = AdminActionAttributes.class.getName() + ".";

    /** 기록 대상 요청이라는 표시이자 요청 시각. preHandle 이 넣는다. */
    public static final String STARTED_AT = PREFIX + "STARTED_AT";
    /** 읽은 본문 객체. {@link AdminActionRequestBodyAdvice}가 넣는다. */
    public static final String BODY = PREFIX + "BODY";
    /** 실패 응답의 code. {@link AdminActionErrorCodeAdvice}가 넣는다. */
    public static final String ERROR_CODE = PREFIX + "ERROR_CODE";

    private AdminActionAttributes() {
    }
}
