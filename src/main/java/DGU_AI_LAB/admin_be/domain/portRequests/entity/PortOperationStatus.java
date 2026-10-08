package DGU_AI_LAB.admin_be.domain.portRequests.entity;

/**
 * 추가 포트 변경 작업의 진행 상태.
 *
 * <pre>
 * PROCESSING → APPLIED(작업 성공, DB 반영) · FAILED(작업 실패 — 변경 요청을 다시 승인하면 이어서 끝난다)
 * </pre>
 */
public enum PortOperationStatus {
    PROCESSING, APPLIED, FAILED
}
