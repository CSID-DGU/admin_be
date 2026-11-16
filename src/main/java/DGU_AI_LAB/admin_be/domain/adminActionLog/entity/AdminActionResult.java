package DGU_AI_LAB.admin_be.domain.adminActionLog.entity;

/**
 * 기록 한 줄의 결과. 저장하지 않고 상태 코드로 계산한다.
 *
 * <pre>
 * 2xx → SUCCESS  (202도 여기. 비동기 작업은 "등록까지 성공"이며 최종 결과는 기존 진행 상태 API로 본다)
 * 4xx → REJECTED (요청이 규칙에 막힘. 사유는 error_code)
 * 5xx → ERROR    (서버 쪽 오류)
 * </pre>
 */
public enum AdminActionResult {
    SUCCESS, REJECTED, ERROR;

    public static AdminActionResult of(int httpStatus) {
        if (httpStatus >= 500) {
            return ERROR;
        }
        if (httpStatus >= 400) {
            return REJECTED;
        }
        return SUCCESS;
    }
}
