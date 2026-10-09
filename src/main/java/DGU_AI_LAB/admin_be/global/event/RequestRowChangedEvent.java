package DGU_AI_LAB.admin_be.global.event;

/**
 * 신청이나 그 포트 행이 새로 생기거나 바뀌거나 지워졌다. 무엇이 어떻게 바뀌었는지는 싣지 않는다 — 받는 쪽이 커밋된 뒤의
 * 현재 상태를 다시 읽는다.
 */
public record RequestRowChangedEvent() {
}
