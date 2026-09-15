package DGU_AI_LAB.admin_be.error.dto;

import DGU_AI_LAB.admin_be.error.ErrorCode;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Getter
public class ErrorResponse {
    private int status;
    private String message;
    private String code;

    public static ErrorResponse of(ErrorCode errorCode) {
        return ErrorResponse.builder()
                .status(errorCode.getHttpStatus().value())
                .message(errorCode.getMessage())
                .code(errorCode.name())
                .build();
    }

    /** 입력값 검증처럼 구체적인 사유를 화면에 보여줘야 할 때 쓴다. message가 비어 있으면 오류 코드의 기본 메시지를 쓴다. */
    public static ErrorResponse of(ErrorCode errorCode, String message) {
        return ErrorResponse.builder()
                .status(errorCode.getHttpStatus().value())
                .message(message == null || message.isBlank() ? errorCode.getMessage() : message)
                .code(errorCode.name())
                .build();
    }
}
