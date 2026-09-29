package DGU_AI_LAB.admin_be.domain.users.entity;

/**
 * 리눅스 계정의 생명주기. UID/GID와 유저네임은 사람에게 영구히 남으므로 이 값과 무관하다.
 *
 * <pre>
 * NONE      → ACTIVE(생성 작업이 계정을 만들거나 되살림)
 * ACTIVE    → RELEASING(계정 회수 시작)
 * RELEASING → NONE(모든 노드에서 회수 작업 성공) · ACTIVE(회수할 노드를 몰라 보류)
 * </pre>
 */
public enum UbuntuAccountStatus {
    /** 원장(AD)에 계정이 없다. 첫 승인 전이거나 회수가 끝난 사람이다. */
    NONE,
    /** 원장에 계정이 살아 있다. */
    ACTIVE,
    /** 회수 작업이 도는 중이다. 컨테이너를 모두 회수한 뒤 노드마다 계정 회수 작업을 등록한다. */
    RELEASING
}
