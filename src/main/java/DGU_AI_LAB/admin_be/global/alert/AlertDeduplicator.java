package DGU_AI_LAB.admin_be.global.alert;

/**
 * 같은 사건에 대한 관리자 알림을 한 번만 보내게 한다. 결과 불명·자원을 남긴 실패처럼 상태가 그대로 남아 주기 작업이
 * 매 바퀴 다시 보는 사건에 쓴다.
 *
 * <p>키는 사건 하나를 가리켜야 한다(예: 작업 종류·신청 번호·작업 번호). 같은 신청이라도 새 작업이면 새 키가 되므로
 * 따로 지울 필요가 없고, 기록은 일정 기간 뒤 저절로 사라진다.
 */
public interface AlertDeduplicator {

    /**
     * 이 사건을 처음 보는가. 처음이면 기록하고 true를 돌려준다 — 호출자는 true일 때만 알린다.
     * 기록 저장소에 닿지 못하면 true를 돌려준다. 알림을 빠뜨리느니 중복되는 편이 낫다.
     */
    boolean firstOccurrence(String eventKey);
}
