package DGU_AI_LAB.admin_be.domain.requests.job;

import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;

/**
 * 작업 등록 요청은 실행기에 닿았을 수 있는데 답을 받지 못했다(응답 시간 초과, 연결 끊김, 읽지 못한 응답).
 * 작업이 등록됐는지 알 수 없으므로, 호출자는 "등록되지 않았다"고 보고 되돌리면 안 된다. 실행기가 답으로 거절한
 * 경우와 요청이 닿지도 못한 경우({@link JobResults#neverReachedServer})는 이 예외가 아니다.
 */
public class JobRegistrationUnconfirmedException extends BusinessException {

    public JobRegistrationUnconfirmedException(String message, ErrorCode errorCode, Throwable cause) {
        super(message, errorCode, cause);
    }
}
