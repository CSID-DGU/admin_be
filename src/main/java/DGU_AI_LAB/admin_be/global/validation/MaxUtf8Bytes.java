package DGU_AI_LAB.admin_be.global.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 문자열의 UTF-8 바이트 길이 상한. {@code @Size}는 글자 수를 세므로, 바이트로 한도가 정해진 값(BCrypt는 72바이트
 * 초과를 예외로 거절한다)은 한글처럼 여러 바이트인 글자에서 {@code @Size}를 통과하고도 서비스에서 500으로 끝난다.
 * null은 통과시킨다 — 필수 여부는 {@code @NotBlank}가 맡는다.
 */
@Documented
@Constraint(validatedBy = MaxUtf8BytesValidator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface MaxUtf8Bytes {

    int value();

    String message() default "입력값이 너무 깁니다.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
