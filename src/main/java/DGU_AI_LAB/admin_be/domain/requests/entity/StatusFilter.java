package DGU_AI_LAB.admin_be.domain.requests.entity;

/**
 * 필터링 쿼리용 enum. DB에 저장되지 않으며 API 파라미터 전용입니다.
 * ALL은 "모든 상태 조회" 의미의 sentinel 값입니다.
 */
public enum StatusFilter {
    PENDING, DENIED, FULFILLED, DELETED, ALL
}
