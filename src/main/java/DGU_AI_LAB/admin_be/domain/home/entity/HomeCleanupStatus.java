package DGU_AI_LAB.admin_be.domain.home.entity;

/**
 * 홈 삭제 시도의 상태.
 *
 * <pre>
 * PROCESSING → DELETED(작업 성공. 홈이 이미 없었던 경우 포함) · FAILED(등록 실패 또는 작업 실패)
 * </pre>
 * 실패한 시도는 그대로 남기고, 다음 날 새 시도를 만든다.
 */
public enum HomeCleanupStatus {
    PROCESSING,
    DELETED,
    FAILED
}
