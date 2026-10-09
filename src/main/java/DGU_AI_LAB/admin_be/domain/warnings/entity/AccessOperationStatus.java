package DGU_AI_LAB.admin_be.domain.warnings.entity;

/**
 * 접속 차단·해제 작업의 진행 상태.
 *
 * <pre>
 * PROCESSING → APPLIED(작업 성공) · FAILED(작업 실패 — 다음 점검 때 새 작업으로 다시 맞춘다)
 * </pre>
 */
public enum AccessOperationStatus {
    PROCESSING, APPLIED, FAILED
}
