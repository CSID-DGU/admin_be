package DGU_AI_LAB.admin_be.domain.alarm.service;

/** 알림 문구에 값을 넣기 전의 공통 손질. */
final class NotificationText {

    private NotificationText() {
    }

    /** 관리자가 메모를 비워 두면 MessageFormat이 "null"을 찍으므로 "없음"으로 바꾼다. */
    static String orNone(String text) {
        return text == null || text.isBlank() ? "없음" : text;
    }
}
