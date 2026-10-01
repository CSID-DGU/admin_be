package DGU_AI_LAB.admin_be.domain.users.entity;

/**
 * 비밀번호 재설정 신청의 상태.
 *
 * <pre>
 * PENDING ──승인──▶ PROCESSING ──작업 성공──▶ APPLIED
 *    │  ▲               │
 *    │  └──작업 실패─────┘
 *    ├──승인(리눅스 계정 없음)──▶ APPLIED
 *    └──거절──▶ DENIED
 * </pre>
 */
public enum PasswordResetStatus {
    /** 관리자 승인을 기다린다. 아직 아무것도 바뀌지 않았다. */
    PENDING,
    /** 승인됐고 컨테이너에 반영하는 작업이 돈다. */
    PROCESSING,
    /** 웹·SSH 비밀번호가 모두 바뀌었다. */
    APPLIED,
    /** 관리자가 거절했다. */
    DENIED
}
