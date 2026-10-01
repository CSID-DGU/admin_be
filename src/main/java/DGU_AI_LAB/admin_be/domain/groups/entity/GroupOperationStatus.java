package DGU_AI_LAB.admin_be.domain.groups.entity;

/**
 * 공용 그룹 작업의 진행 상태.
 *
 * <pre>
 * PROCESSING → APPLIED(작업 성공, DB 반영) · FAILED(작업 실패 — 같은 요청을 다시 내면 이어서 끝난다)
 * </pre>
 */
public enum GroupOperationStatus {
    PROCESSING, APPLIED, FAILED
}
