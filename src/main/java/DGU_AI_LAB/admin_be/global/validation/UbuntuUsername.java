package DGU_AI_LAB.admin_be.global.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 우분투 계정 이름 규칙. 이 이름은 리눅스 계정·홈 디렉터리뿐 아니라 쿠버네티스 자원 이름
 * (Pod, 접속 포트 Service, Kerberos keytab Secret)에 그대로 들어간다.
 *
 * <ul>
 *   <li>3~32자 — 리눅스 계정 이름 상한이 32자다.</li>
 *   <li>소문자로 시작, 소문자·숫자·하이픈만, 소문자나 숫자로 끝남 — 쿠버네티스 이름 규칙(RFC 1123)에는
 *       밑줄이 없어서, 밑줄이 들어간 이름은 Secret 생성 단계에서 실패한다.</li>
 * </ul>
 *
 * <p>시스템 계정·그룹 이름({@link ReservedLinuxNames})은 config-server에서 받아 오는 목록이라 형식 검사가 아닌
 * 서비스 계층에서 거른다.
 */
@Documented
@Constraint(validatedBy = {})
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
@ReportAsSingleViolation
@NotBlank
@Pattern(regexp = UbuntuUsername.REGEX)
public @interface UbuntuUsername {

    String REGEX = "^[a-z][a-z0-9-]{1,30}[a-z0-9]$";

    String message() default "우분투 계정 이름은 3~32자로, 소문자로 시작하고 소문자·숫자·하이픈(-)만 쓸 수 있으며 소문자나 숫자로 끝나야 합니다.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
