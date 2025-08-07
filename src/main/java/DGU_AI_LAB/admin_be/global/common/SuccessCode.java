package DGU_AI_LAB.admin_be.global.common;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public enum SuccessCode {

    /**
     * 200 OK
     */
    OK(HttpStatus.OK, "요청이 성공했습니다."),

    /**
     * 201 Created
     */
    CREATED(HttpStatus.CREATED, "요청이 성공했습니다."),

    /**
     * Email Success
     */
    EMAIL_SENT(HttpStatus.OK, "이메일 인증번호 전송 완료"),
    EMAIL_VERIFIED(HttpStatus.OK, "이메일 인증번호 인증 완료");

    ;
    private final HttpStatus httpStatus;
    private final String message;
}
