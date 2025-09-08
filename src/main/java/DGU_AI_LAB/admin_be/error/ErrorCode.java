package DGU_AI_LAB.admin_be.error;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public enum ErrorCode {
    /**
     * 400 Bad Request
     */
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    MISSING_REQUEST_PARAMETER(HttpStatus.BAD_REQUEST, "필수 요청 파라미터가 누락되었습니다."),


    /**
     * 401 Unauthorized
     */
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "리소스 접근 권한이 없습니다."),
    INVALID_ACCESS_TOKEN(HttpStatus.UNAUTHORIZED, "액세스 토큰의 형식이 올바르지 않습니다. Bearer 타입을 확인해 주세요."),
    INVALID_ACCESS_TOKEN_VALUE(HttpStatus.UNAUTHORIZED, "액세스 토큰의 값이 올바르지 않습니다."),
    EXPIRED_ACCESS_TOKEN(HttpStatus.UNAUTHORIZED, "액세스 토큰이 만료되었습니다. 재발급 받아주세요."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "리프레시 토큰의 형식이 올바르지 않습니다."),
    INVALID_REFRESH_TOKEN_VALUE(HttpStatus.UNAUTHORIZED, "리프레시 토큰의 값이 올바르지 않습니다."),
    EXPIRED_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "리프레시 토큰이 만료되었습니다. 다시 로그인해 주세요."),
    NOT_MATCH_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "일치하지 않는 리프레시 토큰입니다."),
    /**
     * 403 Forbidden
     */
    FORBIDDEN(HttpStatus.FORBIDDEN, "리소스 접근 권한이 없습니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "접근이 거부되었습니다."),


    /**
     * 404 Not Found
     */
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 리소스를 찾을 수 없습니다."),
    ENTITY_NOT_FOUND(HttpStatus.NOT_FOUND, "엔티티를 찾을 수 없습니다."),

    /**
     * 405 Method Not Allowed
     */
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "잘못된 HTTP method 요청입니다."),

    /**
     * 409 Conflict
     */
    CONFLICT(HttpStatus.CONFLICT, "이미 존재하는 리소스입니다."),

    /**
     * 413 Payload Too Large
     */
    FILE_SIZE_EXCEEDED(HttpStatus.PAYLOAD_TOO_LARGE, "이미지 최대 크기를 초과하였습니다."),


    /**
     * 500 Internal Server Error
     */
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류입니다."),
    FILE_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "파일 업로드에 실패하였습니다."),
    JSON_PARSING_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "JSON 파싱에 실패하였습니다."),

    /**
     * 502 Bad Gateway
     */
    SLACK_DM_CHANNEL_FAILED(HttpStatus.BAD_GATEWAY, "Slack DM 채널 열기를 실패하였습니다."),
    EXTERNAL_API_ERROR(HttpStatus.BAD_GATEWAY, "외부 API 호출에 실패했습니다."),

    /**
     * 503 Service Unavailable
     */
    SLACK_SEND_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "Slack 메시지 전송에 실패하였습니다."),

    /**
     * User Error
     */
    EMAIL_NOT_VERIFIED(HttpStatus.UNAUTHORIZED, "이메일 인증이 완료되지 않았습니다."),
    AUTHENTICATION_FAILED(HttpStatus.UNAUTHORIZED, "사용자 인증에 실패하였습니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    USER_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 가입된 사용자입니다."),
    DUPLICATE_NAME(HttpStatus.CONFLICT, "중복된 닉네임입니다."),
    INVALID_LOGIN_INFO(HttpStatus.BAD_REQUEST, "잘못된 로그인 입력값입니다."),
    INVALID_AUTH_CODE(HttpStatus.BAD_REQUEST, "올바르지 않은 인증 코드입니다."),
    GROUP_NOT_FOUND(HttpStatus.NOT_FOUND, "지정된 그룹을 찾을 수 없습니다."),
    UID_ALLOCATION_FAILED(HttpStatus.BAD_REQUEST, "UID를 할당에 실패했습니다."),
    DUPLICATE_USERNAME(HttpStatus.CONFLICT, "이미 사용하고 있는 username입니다. 같은 사용자이더라도 다른 username을 입력해주세요."),

    SLACK_USER_NOT_FOUND(HttpStatus.NOT_FOUND, "Slack 사용자를 찾을 수 없습니다."),
    SLACK_USER_EMAIL_NOT_MATCH(HttpStatus.NOT_FOUND, "이메일이 일치하는 Slack 사용자를 찾을 수 없습니다."),

    INVALID_PASSWORD(HttpStatus.UNAUTHORIZED, "현재 비밀번호가 일치하지 않습니다."),
    PASSWORD_CHANGE_SAME_AS_OLD(HttpStatus.BAD_REQUEST, "새 비밀번호가 현재 비밀번호와 동일합니다."),

    /**
     * Group Error
     */
    NO_AVAILABLE_GROUPS(HttpStatus.NOT_FOUND, "존재하는 그룹 정보가 없습니다."),
    DUPLICATE_GROUP_ID(HttpStatus.CONFLICT, "외부 API: 그룹명 또는 GID 충돌"),
    DUPLICATE_GROUP_NAME(HttpStatus.CONFLICT, "중복된 그룹 이름입니다."),
    GROUP_CREATION_FAILED(HttpStatus.BAD_GATEWAY, "외부 API: 필수 필드 누락 또는 형식 오류 "),
    GID_ALLOCATION_FAILED(HttpStatus.BAD_GATEWAY, "IdAllocationService에서 GID 할당에 실패했습니다. "),
    FORBIDDEN_REQUEST(HttpStatus.BAD_REQUEST, "요청된 우분투 사용자 이름은 로그인한 사용자의 계정이 아닙니다."),
    INVALID_GROUP_MEMBER(HttpStatus.BAD_REQUEST, "존재하지 않는 사용자입니다."),


    /**
     * Approval Error
     */
    USER_APPROVAL_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 유저의 승인 정보를 찾을 수 없습니다."),

    /**
     * Resource Group Error
     */
    RESOURCE_GROUP_NOT_FOUND(HttpStatus.NOT_FOUND, "리소스 그룹을 찾을 수 없습니다."),
    NO_AVAILABLE_RESOURCES(HttpStatus.NOT_FOUND, "사용 가능한 리소스가 없습니다."),

    /**
     * Node Error
     */
    NODE_NOT_FOUND(HttpStatus.NOT_FOUND, "노드를 찾을 수 없습니다."),

    /**
     * Request Error
     */
    INVALID_REQUEST_STATUS(HttpStatus.CONFLICT, "이미 처리된 신청입니다."),
    //FORBIDDEN_REQUEST(HttpStatus.BAD_REQUEST, "본인의 신청만 변경 신청할 수 있습니다."),
    UNSUPPORTED_CHANGE_TYPE(HttpStatus.BAD_REQUEST, "지원되지 않는 요청 타입(enum)입니다."),

    /**
     * PVC Error
     */
    PVC_API_FAILURE(HttpStatus.BAD_GATEWAY, "pvc 관련 API 요청에 실패했습니다."),
    UBUNTU_USER_DELETION_FAILED(HttpStatus.BAD_GATEWAY, "우분투 계정 삭제에 실패했습니다."),
    USER_CREATION_FAILED(HttpStatus.BAD_GATEWAY, "사용자 계정 생성에 실패했습니다."),
    INVALID_USERNAME_FORMAT(HttpStatus.BAD_REQUEST, "잘못된 사용자명 형식입니다.")

    ;
    private final HttpStatus httpStatus;
    private final String message;

}